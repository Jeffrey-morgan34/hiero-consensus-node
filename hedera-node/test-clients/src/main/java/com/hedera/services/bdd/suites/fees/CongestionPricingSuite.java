/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

@HapiTestSuite
public class CongestionPricingSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CongestionPricingSuite.class);

    private static final String FEES_PERCENT_CONGESTION_MULTIPLIERS = "fees.percentCongestionMultipliers";
    private static final String ACTIVE_PROFILE_PROPERTY = "hedera.profiles.active";
    private static final String defaultCongestionMultipliers =
            HapiSpecSetup.getDefaultNodeProps().get(FEES_PERCENT_CONGESTION_MULTIPLIERS);
    private static final String FEES_MIN_CONGESTION_PERIOD = "fees.minCongestionPeriod";
    private static final String defaultMinCongestionPeriod =
            HapiSpecSetup.getDefaultNodeProps().get(FEES_MIN_CONGESTION_PERIOD);
    private static final String CIVILIAN_ACCOUNT = "civilian";
    private static final String SECOND_ACCOUNT = "second";
    private static final String FEE_MONITOR_ACCOUNT = "feeMonitor";

    public static void main(String... args) {
        new CongestionPricingSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {canUpdateMultipliersDynamically(), canUpdateMultipliersDynamically2()});
    }

    @HapiTest
    final HapiSpec canUpdateMultipliersDynamically() {
        var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-congestion.json");
        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
        var contract = "Multipurpose";
        String tmpMinCongestionPeriod = "1";

        AtomicLong normalPrice = new AtomicLong();
        AtomicLong sevenXPrice = new AtomicLong();

        return propertyPreservingHapiSpec("CanUpdateMultipliersDynamically")
                .preserving(ACTIVE_PROFILE_PROPERTY)
                .given(
                        overriding(ACTIVE_PROFILE_PROPERTY, "DEV"),
                        cryptoCreate(CIVILIAN_ACCOUNT).payingWith(GENESIS).balance(ONE_MILLION_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract),
                        contractCall(contract)
                                .payingWith(CIVILIAN_ACCOUNT)
                                .fee(ONE_HUNDRED_HBARS)
                                .sending(ONE_HBAR)
                                .via("cheapCall"),
                        getTxnRecord("cheapCall")
                                .providingFeeTo(normalFee -> {
                                    log.info("Normal fee is {}", normalFee);
                                    normalPrice.set(normalFee);
                                })
                                .logged())
                .when(
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(Map.of(
                                        FEES_PERCENT_CONGESTION_MULTIPLIERS,
                                        "1,7x",
                                        FEES_MIN_CONGESTION_PERIOD,
                                        tmpMinCongestionPeriod)),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(artificialLimits.toByteArray()),
                        sleepFor(2_000),
                        blockingOrder(IntStream.range(0, 10)
                                .mapToObj(i -> new HapiSpecOperation[] {
                                    usableTxnIdNamed("uncheckedTxn" + i).payerId(CIVILIAN_ACCOUNT),
                                    uncheckedSubmit(contractCall(contract)
                                                    .signedBy(CIVILIAN_ACCOUNT)
                                                    .fee(ONE_HUNDRED_HBARS)
                                                    .sending(ONE_HBAR)
                                                    .txnId("uncheckedTxn" + i))
                                            .payingWith(GENESIS),
                                    sleepFor(125)
                                })
                                .flatMap(Arrays::stream)
                                .toArray(HapiSpecOperation[]::new)),
                        contractCall(contract)
                                .payingWith(CIVILIAN_ACCOUNT)
                                .fee(ONE_HUNDRED_HBARS)
                                .sending(ONE_HBAR)
                                .via("pricyCall"))
                .then(
                        getReceipt("pricyCall").logged(),
                        getTxnRecord("pricyCall").payingWith(GENESIS).providingFeeTo(congestionFee -> {
                            log.info("Congestion fee is {}", congestionFee);
                            sevenXPrice.set(congestionFee);
                        }),

                        /* Make sure the multiplier is reset before the next spec runs */
                        fileUpdate(THROTTLE_DEFS)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles.toByteArray()),
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(Map.of(
                                        FEES_PERCENT_CONGESTION_MULTIPLIERS, defaultCongestionMultipliers,
                                        FEES_MIN_CONGESTION_PERIOD, defaultMinCongestionPeriod)),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
                                .payingWith(GENESIS),

                        /* Check for error after resetting settings. */
                        withOpContext((spec, opLog) -> Assertions.assertEquals(
                                7.0,
                                (1.0 * sevenXPrice.get()) / normalPrice.get(),
                                0.1,
                                "~7x multiplier should be in affect!")));
    }

    @HapiTest
    final HapiSpec canUpdateMultipliersDynamically2() {
        var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-congestion.json");
        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
        String tmpMinCongestionPeriod = "1";

        AtomicLong normalPrice = new AtomicLong();
        AtomicLong sevenXPrice = new AtomicLong();

        return propertyPreservingHapiSpec("CanUpdateMultipliersDynamically2")
                .preserving(ACTIVE_PROFILE_PROPERTY)
                .given(
                        overriding(ACTIVE_PROFILE_PROPERTY, "DEV"),
                        cryptoCreate(CIVILIAN_ACCOUNT).payingWith(GENESIS).balance(ONE_MILLION_HBARS),
                        cryptoCreate(SECOND_ACCOUNT).payingWith(GENESIS).balance(ONE_HBAR),
                        cryptoCreate(FEE_MONITOR_ACCOUNT).payingWith(GENESIS).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(CIVILIAN_ACCOUNT, SECOND_ACCOUNT, 5L))
                                .payingWith(FEE_MONITOR_ACCOUNT)
                                .via("cheapCall"),
                        getAccountInfo(FEE_MONITOR_ACCOUNT)
                                .exposingBalance(balance -> {
                                    log.info("Normal fee is {}", ONE_MILLION_HBARS - balance);
                                    normalPrice.set(ONE_MILLION_HBARS - balance);
                                })
                                .logged())
                .when(
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(Map.of(
                                        FEES_PERCENT_CONGESTION_MULTIPLIERS,
                                        "1,7x",
                                        FEES_MIN_CONGESTION_PERIOD,
                                        tmpMinCongestionPeriod)),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(artificialLimits.toByteArray()),
                        sleepFor(2_000),
                        blockingOrder(IntStream.range(0, 20)
                                .mapToObj(i -> new HapiSpecOperation[] {
                                    usableTxnIdNamed("uncheckedTxn" + i).payerId(CIVILIAN_ACCOUNT),
                                    uncheckedSubmit(cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(
                                                            CIVILIAN_ACCOUNT, SECOND_ACCOUNT, 5L))
                                                    .txnId("uncheckedTxn" + i))
                                            .payingWith(GENESIS),
                                    sleepFor(125)
                                })
                                .flatMap(Arrays::stream)
                                .toArray(HapiSpecOperation[]::new)),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(CIVILIAN_ACCOUNT, SECOND_ACCOUNT, 5L))
                                .payingWith(FEE_MONITOR_ACCOUNT)
                                .fee(ONE_HUNDRED_HBARS)
                                .via("pricyCall"))
                .then(
                        getAccountInfo(FEE_MONITOR_ACCOUNT)
                                .exposingBalance(balance -> {
                                    log.info("Congestion fee is {}", ONE_MILLION_HBARS - normalPrice.get() - balance);
                                    sevenXPrice.set(ONE_MILLION_HBARS - normalPrice.get() - balance);
                                })
                                .logged(),

                        /* Make sure the multiplier is reset before the next spec runs */
                        fileUpdate(THROTTLE_DEFS)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles.toByteArray()),
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(Map.of(
                                        FEES_PERCENT_CONGESTION_MULTIPLIERS, defaultCongestionMultipliers,
                                        FEES_MIN_CONGESTION_PERIOD, defaultMinCongestionPeriod)),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
                                .payingWith(GENESIS),

                        /* Check for error after resetting settings. */
                        withOpContext((spec, opLog) -> Assertions.assertEquals(
                                7.0,
                                (1.0 * sevenXPrice.get()) / normalPrice.get(),
                                0.1,
                                "~7x multiplier should be in affect!")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

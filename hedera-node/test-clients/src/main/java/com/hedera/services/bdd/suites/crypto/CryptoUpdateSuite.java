/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyLabels.complex;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.AUTO_CREATION_KEY_NAME_FN;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createHip32Auto;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createHollow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfigNow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleNonSyntheticItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withAddressOfKey;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.ZERO_BYTE_MEMO;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_MAX_AUTO_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyLabels;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsValidator;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class CryptoUpdateSuite {
    private static final String TEST_ACCOUNT = "testAccount";
    private static final String TARGET_ACCOUNT = "complexKeyAccount";
    private static final String ACCOUNT_ALICE = "alice";
    private static final String ACCOUNT_PETER = "peter";
    private static final String ACCOUNT_PARKER = "parker";
    private static final String ACCOUNT_TONY = "tony";
    private static final String ACCOUNT_STARK = "stark";

    private static final String TOKEN_FUNGIBLE = "fungibleToken";

    private static final String REPEATING_KEY = "repeatingKey";
    private static final String ORIG_KEY = "origKey";
    private static final String UPD_KEY = "updKey";
    private static final String TARGET_KEY = "twoLevelThreshWithOverlap";
    private static final String MULTI_KEY = "multiKey";

    private static final SigControl twoLevelThresh = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, ANY, ANY, ANY, ANY, ANY, ANY, ANY),
            SigControl.threshSigs(3, ANY, ANY, ANY, ANY, ANY, ANY, ANY));
    private static final KeyLabels overlappingKeys =
            complex(complex("A", "B", "C", "D", "E", "F", "G"), complex("H", "I", "J", "K", "L", "M", "A"));

    private static final SigControl ENOUGH_UNIQUE_SIGS = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
            SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
    private static final SigControl NOT_ENOUGH_UNIQUE_SIGS = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
            SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
    private static final SigControl ENOUGH_OVERLAPPING_SIGS = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
            SigControl.threshSigs(3, ON, ON, OFF, OFF, OFF, OFF, ON));

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(cryptoCreate("user").stakedAccountId("0.0.20").declinedReward(true))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> cryptoUpdate("user")
                        .newStakedAccountId("0.0.21")));
    }

    private static final String[] ACCOUNTS_TO_HAVE_KEYS_ROTATED = {
        "longZero", "autoCreated", "hollowAccount", "explicitAlias"
    };
    private static final UnaryOperator<String> ROTATION_TXN = account -> account + "KeyRotation";

    /**
     * Creates four accounts with ECDSA keys, the first having a long-zero EVM address and the other three having
     * arbitrary EVM addresses created via,
     * <ol>
     *     <li>Legacy HIP-32 auto-account creation via transfer to a key alias.</li>
     *     <li>Hollow account creation via transfer to an EVM address.</li>
     *     <li>Explicit HIP-583 specification of the EVM address on creation.</li>
     * </ol>
     * Then asserts that each of them have the expected EVM addresses in the {@link HapiGetAccountInfo} query both
     * before and after key rotation; and that the record stream does not imply anything different.
     */
    @HapiTest
    final Stream<DynamicTest> keyRotationDoesNotChangeEvmAddress() {
        final Map<String, Address> evmAddresses = new HashMap<>();
        final var allTxnIds = Stream.concat(
                        Arrays.stream(ACCOUNTS_TO_HAVE_KEYS_ROTATED),
                        Arrays.stream(ACCOUNTS_TO_HAVE_KEYS_ROTATED).map(ROTATION_TXN))
                .toArray(String[]::new);
        return hapiTest(
                recordStreamMustIncludePassFrom(
                        visibleNonSyntheticItems(keyRotationsValidator(evmAddresses), allTxnIds),
                        Duration.ofSeconds(10)),
                // If the FileAlterationObserver just started the monitor, there's a chance we could miss the
                // first couple of creations, so wait for a new record file boundary
                doingContextual(TxnUtils::triggerAndCloseAtLeastOneFileIfNotInterrupted),
                // --- CREATE ACCOUNTS ---
                // The account with a long-zero EVM address
                cryptoCreate("longZero")
                        .via("longZero")
                        .keyShape(SECP256K1_ON)
                        .exposingEvmAddressTo(address -> evmAddresses.put("longZero", address)),
                // The auto-created account with an ECDSA key alias
                createHip32Auto(1, KeyShape.SECP256K1, i -> "autoCreated"),
                withAddressOfKey("autoCreated", evmAddress -> {
                    evmAddresses.put("autoCreated", evmAddress);
                    return withOpContext((spec, opLog) -> spec.registry()
                            .saveTxnId(
                                    "autoCreated",
                                    spec.registry().getTxnId("hip32" + AUTO_CREATION_KEY_NAME_FN.apply(0))));
                }),
                // The hollow account - create and complete it for convenience
                createHollow(
                        1,
                        i -> "hollowAccount",
                        evmAddress -> cryptoTransfer(tinyBarsFromTo(GENESIS, evmAddress, ONE_HUNDRED_HBARS))),
                withAddressOfKey("hollowAccount", evmAddress -> {
                    evmAddresses.put("hollowAccount", evmAddress);
                    return withOpContext((spec, opLog) -> spec.registry()
                            .saveTxnId("hollowAccount", spec.registry().getTxnId("autoCreate" + evmAddress)));
                }),
                cryptoTransfer(tinyBarsFromTo("hollowAccount", FUNDING, 1))
                        .payingWith("hollowAccount")
                        .sigMapPrefixes(uniqueWithFullPrefixesFor("hollowAccount")),
                // The account with an explicit EVM address
                newKeyNamed("bEcdsaKey").shape(KeyShape.SECP256K1),
                withAddressOfKey("bEcdsaKey", evmAddress -> {
                    evmAddresses.put("explicitAlias", evmAddress);
                    return cryptoCreate("explicitAlias")
                            .key("bEcdsaKey")
                            .evmAddress(evmAddress)
                            .via("explicitAlias");
                }),
                // --- ROTATE KEYS ---
                blockingOrder(IntStream.range(0, ACCOUNTS_TO_HAVE_KEYS_ROTATED.length)
                        .mapToObj(i -> {
                            final var newKey = "replKey" + i;
                            final var targetAccount = ACCOUNTS_TO_HAVE_KEYS_ROTATED[i];
                            return blockingOrder(
                                    newKeyNamed(newKey).shape(KeyShape.SECP256K1),
                                    cryptoUpdate(targetAccount).key(newKey).via(ROTATION_TXN.apply(targetAccount)));
                        })
                        .toArray(SpecOperation[]::new)));
    }

    private static VisibleItemsValidator keyRotationsValidator(@NonNull final Map<String, Address> evmAddresses) {
        return (spec, records) -> {
            for (final var txnId : ACCOUNTS_TO_HAVE_KEYS_ROTATED) {
                final var successItems = requireNonNull(records.get(txnId), txnId + " not found");
                final var creationEntry = successItems.entries().stream()
                        .filter(entry -> entry.function() == CryptoCreate)
                        .findFirst()
                        .orElseThrow();
                final var recordEvmAddress = creationEntry.transactionRecord().getEvmAddress();
                final var bodyEvmAddress =
                        creationEntry.body().getCryptoCreateAccount().getAlias();
                final var numEvmAddresses =
                        ((recordEvmAddress.size() == 20) ? 1 : 0) + ((bodyEvmAddress.size() == 20) ? 1 : 0);
                assertTrue(numEvmAddresses <= 1);
                final var evmAddress = numEvmAddresses == 0
                        ? headlongAddressOf(creationEntry.createdAccountId())
                        : asHeadlongAddress(
                                (recordEvmAddress.size() == 20)
                                        ? recordEvmAddress.toByteArray()
                                        : bodyEvmAddress.toByteArray());
                assertEquals(evmAddresses.get(txnId), evmAddress);
                allRunFor(
                        spec,
                        getAccountInfo("0.0." + creationEntry.createdAccountId().accountNumOrThrow())
                                .has(accountWith().evmAddress(ByteString.copyFrom(explicitFromHeadlong(evmAddress)))));
            }
            final var rotationTxnIds = Arrays.stream(ACCOUNTS_TO_HAVE_KEYS_ROTATED)
                    .map(ROTATION_TXN)
                    .toArray(String[]::new);
            for (final var txnId : rotationTxnIds) {
                final var successItems = requireNonNull(records.get(txnId), txnId + " not found");
                final var updateEntry = successItems.entries().stream()
                        .filter(entry -> entry.function() == CryptoUpdate)
                        .findFirst()
                        .orElseThrow();
                assertEquals(0, updateEntry.txnRecord().getEvmAddress().size());
            }
        };
    }

    @HapiTest
    final Stream<DynamicTest> updateForMaxAutoAssociationsForAccountsWorks() {
        return defaultHapiSpec("updateForMaxAutoAssociationsForAccountsWorks")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT_ALICE).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(0),
                        cryptoCreate(ACCOUNT_PETER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(-1),
                        cryptoCreate(ACCOUNT_TONY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ACCOUNT_STARK).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(-1),
                        cryptoCreate(ACCOUNT_PARKER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(-1),
                        tokenCreate(TOKEN_FUNGIBLE)
                                .initialSupply(1000L)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .treasury(ACCOUNT_ALICE)
                                .via("tokenCreate"),
                        tokenAssociate(ACCOUNT_PETER, TOKEN_FUNGIBLE),
                        tokenAssociate(ACCOUNT_TONY, TOKEN_FUNGIBLE))
                .when(
                        // Update Alice
                        cryptoUpdate(ACCOUNT_ALICE).maxAutomaticAssociations(0),
                        getAccountInfo(ACCOUNT_ALICE).hasMaxAutomaticAssociations(0),
                        cryptoUpdate(ACCOUNT_ALICE).maxAutomaticAssociations(-1),
                        getAccountInfo(ACCOUNT_ALICE).hasMaxAutomaticAssociations(-1),
                        // Update Tony
                        cryptoUpdate(ACCOUNT_TONY).maxAutomaticAssociations(1),
                        getAccountInfo(ACCOUNT_TONY).hasMaxAutomaticAssociations(1),
                        // Update Stark
                        cryptoUpdate(ACCOUNT_STARK).maxAutomaticAssociations(-1),
                        getAccountInfo(ACCOUNT_STARK).hasMaxAutomaticAssociations(-1),
                        // Update Peter
                        cryptoUpdate(ACCOUNT_PETER).maxAutomaticAssociations(-1),
                        getAccountInfo(ACCOUNT_PETER).hasMaxAutomaticAssociations(-1),
                        cryptoUpdate(ACCOUNT_PETER).maxAutomaticAssociations(0),
                        getAccountInfo(ACCOUNT_PETER).hasMaxAutomaticAssociations(0),
                        // Update Parker
                        cryptoUpdate(ACCOUNT_PARKER).maxAutomaticAssociations(1),
                        getAccountInfo(ACCOUNT_PARKER).hasMaxAutomaticAssociations(1))
                .then(getTxnRecord("tokenCreate").hasNewTokenAssociation(TOKEN_FUNGIBLE, ACCOUNT_ALICE));
    }

    @HapiTest
    final Stream<DynamicTest> updateStakingFieldsWorks() {
        return hapiTest(
                newKeyNamed(ADMIN_KEY),
                cryptoCreate("user").key(ADMIN_KEY).stakedAccountId("0.0.20").declinedReward(true),
                getAccountInfo("user")
                        .has(accountWith()
                                .stakedAccountId("0.0.20")
                                .noStakingNodeId()
                                .isDeclinedReward(true)),
                cryptoUpdate("user").newStakedNodeId(0L).newDeclinedReward(false),
                getAccountInfo("user")
                        .has(accountWith().noStakedAccountId().stakedNodeId(0L).isDeclinedReward(false)),
                cryptoUpdate("user").newStakedNodeId(-1L),
                cryptoUpdate("user").newStakedNodeId(-25L).hasKnownStatus(INVALID_STAKING_ID),
                getAccountInfo("user")
                        .has(accountWith().noStakedAccountId().noStakingNodeId().isDeclinedReward(false)),
                cryptoUpdate("user").key(ADMIN_KEY).newStakedAccountId("0.0.20").newDeclinedReward(true),
                getAccountInfo("user")
                        .has(accountWith()
                                .stakedAccountId("0.0.20")
                                .noStakingNodeId()
                                .isDeclinedReward(true))
                        .logged(),
                cryptoUpdate("user").key(ADMIN_KEY).newStakedAccountId("0.0.0"),
                getAccountInfo("user")
                        .has(accountWith().noStakedAccountId().noStakingNodeId().isDeclinedReward(true))
                        .logged(),
                // For completeness stake back to a node
                cryptoUpdate("user").key(ADMIN_KEY).newStakedNodeId(1),
                getAccountInfo("user").has(accountWith().stakedNodeId(1L).isDeclinedReward(true)));
    }

    @LeakyHapiTest(overrides = {"entities.maxLifetime", "ledger.maxAutoAssociations"})
    final Stream<DynamicTest> usdFeeAsExpectedCryptoUpdate() {
        double baseFee = 0.000214;
        double baseFeeWithExpiry = 0.00022;

        final var baseTxn = "baseTxn";
        final var plusOneTxn = "plusOneTxn";
        final var plusTenTxn = "plusTenTxn";
        final var plusFiveKTxn = "plusFiveKTxn";
        final var plusFiveKAndOneTxn = "plusFiveKAndOneTxn";
        final var invalidNegativeTxn = "invalidNegativeTxn";
        final var validNegativeTxn = "validNegativeTxn";
        final var allowedPercentDiff = 1.5;

        AtomicLong expiration = new AtomicLong();
        return hapiTest(
                overridingTwo(
                        "ledger.maxAutoAssociations", "5000",
                        "entities.maxLifetime", "3153600000"),
                newKeyNamed("key").shape(SIMPLE),
                cryptoCreate("payer").key("key").balance(1_000 * ONE_HBAR),
                cryptoCreate("canonicalAccount")
                        .key("key")
                        .balance(100 * ONE_HBAR)
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .blankMemo()
                        .payingWith("payer"),
                cryptoCreate("autoAssocTarget")
                        .key("key")
                        .balance(100 * ONE_HBAR)
                        .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                        .blankMemo()
                        .payingWith("payer"),
                getAccountInfo("canonicalAccount").exposingExpiry(expiration::set),
                sourcing(() -> cryptoUpdate("canonicalAccount")
                        .payingWith("canonicalAccount")
                        .expiring(expiration.get() + THREE_MONTHS_IN_SECONDS)
                        .blankMemo()
                        .via(baseTxn)),
                getAccountInfo("canonicalAccount")
                        .hasMaxAutomaticAssociations(0)
                        .logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(1)
                        .via(plusOneTxn),
                getAccountInfo("autoAssocTarget").hasMaxAutomaticAssociations(1).logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(11)
                        .via(plusTenTxn),
                getAccountInfo("autoAssocTarget")
                        .hasMaxAutomaticAssociations(11)
                        .logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(5000)
                        .via(plusFiveKTxn),
                getAccountInfo("autoAssocTarget")
                        .hasMaxAutomaticAssociations(5000)
                        .logged(),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(-1000)
                        .via(invalidNegativeTxn)
                        .hasKnownStatus(INVALID_MAX_AUTO_ASSOCIATIONS),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(5001)
                        .via(plusFiveKAndOneTxn)
                        .hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                cryptoUpdate("autoAssocTarget")
                        .payingWith("autoAssocTarget")
                        .blankMemo()
                        .maxAutomaticAssociations(-1)
                        .via(validNegativeTxn),
                getAccountInfo("autoAssocTarget")
                        .hasMaxAutomaticAssociations(-1)
                        .logged(),
                validateChargedUsd(baseTxn, baseFeeWithExpiry, allowedPercentDiff)
                        .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                validateChargedUsd(plusOneTxn, baseFee, allowedPercentDiff)
                        .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                validateChargedUsd(plusTenTxn, baseFee, allowedPercentDiff)
                        .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                validateChargedUsd(plusFiveKTxn, baseFee, allowedPercentDiff)
                        .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                validateChargedUsd(validNegativeTxn, baseFee, allowedPercentDiff)
                        .skippedIfAutoScheduling(Set.of(CryptoUpdate)));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsWithOverlyLongLifetime() {
        return defaultHapiSpec("UpdateFailsWithOverlyLongLifetime")
                .given(cryptoCreate(TARGET_ACCOUNT))
                .when()
                .then(doWithStartupConfigNow("entities.maxLifetime", (value, now) -> cryptoUpdate(TARGET_ACCOUNT)
                        .expiring(now.getEpochSecond() + Long.parseLong(value) + 12345L)
                        .hasKnownStatus(INVALID_EXPIRATION_TIME)));
    }

    @HapiTest
    final Stream<DynamicTest> sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign() {
        String sysAccount = "0.0.99";
        String randomAccount = "randomAccount";
        String firstKey = "firstKey";
        String secondKey = "secondKey";

        return defaultHapiSpec("sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign")
                .given(
                        newKeyNamed(firstKey).shape(SIMPLE),
                        newKeyNamed(secondKey).shape(SIMPLE))
                .when(cryptoCreate(randomAccount).key(firstKey))
                .then(
                        cryptoUpdate(sysAccount)
                                .key(secondKey)
                                .signedBy(GENESIS)
                                .payingWith(GENESIS)
                                .hasKnownStatus(SUCCESS)
                                .logged(),
                        cryptoUpdate(randomAccount)
                                .key(secondKey)
                                .signedBy(firstKey)
                                .payingWith(GENESIS)
                                .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> canUpdateMemo() {
        String firstMemo = "First";
        String secondMemo = "Second";
        return defaultHapiSpec("CanUpdateMemo")
                .given(cryptoCreate(TARGET_ACCOUNT).balance(0L).entityMemo(firstMemo))
                .when(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .entityMemo(ZERO_BYTE_MEMO)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        cryptoUpdate(TARGET_ACCOUNT).entityMemo(secondMemo))
                .then(getAccountDetails(TARGET_ACCOUNT)
                        .payingWith(GENESIS)
                        .has(accountDetailsWith().memo(secondMemo)));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithUniqueSigs() {
        return hapiTest(
                newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY),
                cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(TARGET_KEY, ENOUGH_UNIQUE_SIGS))
                        .receiverSigRequired(true));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithOneEffectiveSig() {
        KeyLabels oneUniqueKey =
                complex(complex("X", "X", "X", "X", "X", "X", "X"), complex("X", "X", "X", "X", "X", "X", "X"));
        SigControl singleSig = SigControl.threshSigs(
                2,
                SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
                SigControl.threshSigs(3, OFF, OFF, OFF, ON, OFF, OFF, OFF));

        return hapiTest(
                newKeyNamed(REPEATING_KEY).shape(twoLevelThresh).labels(oneUniqueKey),
                cryptoCreate(TARGET_ACCOUNT).key(REPEATING_KEY).balance(1_000_000_000L),
                cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(REPEATING_KEY, singleSig))
                        .receiverSigRequired(true)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithOverlappingSigs() {
        return hapiTest(
                newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY),
                cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(TARGET_KEY, ENOUGH_OVERLAPPING_SIGS))
                        .receiverSigRequired(true)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsWithContractKey() {
        AtomicLong id = new AtomicLong();
        final var CONTRACT = "Multipurpose";
        return hapiTest(
                cryptoCreate(TARGET_ACCOUNT),
                uploadInitCode(CONTRACT),
                contractCreate(CONTRACT).exposingNumTo(id::set),
                sourcing(() -> cryptoUpdate(TARGET_ACCOUNT)
                        .protoKey(Key.newBuilder()
                                .setContractID(ContractID.newBuilder()
                                        .setContractNum(id.get())
                                        .build())
                                .build())
                        .hasKnownStatus(INVALID_SIGNATURE)));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsWithInsufficientSigs() {
        return hapiTest(
                newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY),
                cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(TARGET_KEY, NOT_ENOUGH_UNIQUE_SIGS))
                        .receiverSigRequired(true)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> cannotSetThresholdNegative() {
        return hapiTest(cryptoCreate(TEST_ACCOUNT), cryptoUpdate(TEST_ACCOUNT).sendThreshold(-1L));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsIfMissingSigs() {
        SigControl origKeySigs = SigControl.threshSigs(3, ON, ON, SigControl.threshSigs(1, OFF, ON));
        SigControl updKeySigs = SigControl.listSigs(ON, OFF, SigControl.threshSigs(1, ON, OFF, OFF, OFF));

        return hapiTest(
                newKeyNamed(ORIG_KEY).shape(origKeySigs),
                newKeyNamed(UPD_KEY).shape(updKeySigs),
                cryptoCreate(TEST_ACCOUNT)
                        .receiverSigRequired(true)
                        .key(ORIG_KEY)
                        .sigControl(forKey(ORIG_KEY, origKeySigs)),
                cryptoUpdate(TEST_ACCOUNT)
                        .key(UPD_KEY)
                        .sigControl(forKey(TEST_ACCOUNT, origKeySigs), forKey(UPD_KEY, updKeySigs))
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithEmptyKeyFails() {
        SigControl updKeySigs = threshOf(0, 0);

        return hapiTest(
                newKeyNamed(ORIG_KEY).shape(KeyShape.SIMPLE),
                newKeyNamed(UPD_KEY).shape(updKeySigs),
                cryptoCreate(TEST_ACCOUNT).key(ORIG_KEY),
                cryptoUpdate(TEST_ACCOUNT).key(UPD_KEY).hasPrecheck(INVALID_ADMIN_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> updateMaxAutoAssociationsWorks() {
        final int maxAllowedAssociations = 5000;
        final int originalMax = 2;
        final int newBadMax = originalMax - 1;
        final int newGoodMax = originalMax + 1;
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";

        final String treasury = "treasury";
        final String tokenACreate = "tokenACreate";
        final String tokenBCreate = "tokenBCreate";
        final String transferAToC = "transferAToC";
        final String transferBToC = "transferBToC";
        final String CONTRACT = "Multipurpose";
        final String ADMIN_KEY = "adminKey";

        return hapiTest(
                cryptoCreate(treasury).balance(ONE_HUNDRED_HBARS),
                newKeyNamed(ADMIN_KEY),
                uploadInitCode(CONTRACT),
                contractCreate(CONTRACT).adminKey(ADMIN_KEY).maxAutomaticTokenAssociations(originalMax),
                tokenCreate(tokenA)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(treasury)
                        .via(tokenACreate),
                getTxnRecord(tokenACreate).hasNewTokenAssociation(tokenA, treasury),
                tokenCreate(tokenB)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(treasury)
                        .via(tokenBCreate),
                getTxnRecord(tokenBCreate).hasNewTokenAssociation(tokenB, treasury),
                getContractInfo(CONTRACT).has(ContractInfoAsserts.contractWith().maxAutoAssociations(originalMax)),
                cryptoTransfer(moving(1, tokenA).between(treasury, CONTRACT)).via(transferAToC),
                getTxnRecord(transferAToC).hasNewTokenAssociation(tokenA, CONTRACT),
                cryptoTransfer(moving(1, tokenB).between(treasury, CONTRACT)).via(transferBToC),
                getTxnRecord(transferBToC).hasNewTokenAssociation(tokenB, CONTRACT),
                getContractInfo(CONTRACT)
                        .payingWith(GENESIS)
                        .has(contractWith()
                                .hasAlreadyUsedAutomaticAssociations(originalMax)
                                .maxAutoAssociations(originalMax)),
                contractUpdate(CONTRACT)
                        .newMaxAutomaticAssociations(newBadMax)
                        .hasKnownStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT),
                contractUpdate(CONTRACT).newMaxAutomaticAssociations(newGoodMax),
                getContractInfo(CONTRACT).has(contractWith().maxAutoAssociations(newGoodMax)),
                contractUpdate(CONTRACT)
                        .newMaxAutomaticAssociations(maxAllowedAssociations + 1)
                        .hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                contractUpdate(CONTRACT).newMaxAutomaticAssociations(-2).hasKnownStatus(INVALID_MAX_AUTO_ASSOCIATIONS),
                contractUpdate(CONTRACT).newMaxAutomaticAssociations(-1).hasKnownStatus(SUCCESS),
                getContractInfo(CONTRACT).has(contractWith().maxAutoAssociations(-1)));
    }

    @HapiTest
    final Stream<DynamicTest> deletedAccountCannotBeUpdated() {
        final var accountToDelete = "accountToDelete";
        return hapiTest(
                cryptoCreate(accountToDelete).declinedReward(false),
                cryptoDelete(accountToDelete),
                cryptoUpdate(accountToDelete)
                        .payingWith(DEFAULT_PAYER)
                        .newDeclinedReward(true)
                        .hasKnownStatus(ACCOUNT_DELETED));
    }
}

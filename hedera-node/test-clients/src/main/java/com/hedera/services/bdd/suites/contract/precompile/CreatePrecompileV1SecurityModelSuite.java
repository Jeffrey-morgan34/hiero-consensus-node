/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Some of the test cases cannot be converted to use eth calls,
// since they use admin keys, which are held by the txn payer.
// In the case of an eth txn, we revoke the payers keys and the txn would fail.
// The only way an eth account to create a token is the admin key to be of a contractId type.
@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class CreatePrecompileV1SecurityModelSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreatePrecompileV1SecurityModelSuite.class);

    public static final String ACCOUNT_2 = "account2";
    public static final String CONTRACT_ADMIN_KEY = "contractAdminKey";
    public static final String ACCOUNT_TO_ASSOCIATE = "account3";
    public static final String ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
    public static final String FALSE = "false";
    private static final long GAS_TO_OFFER = 1_000_000L;
    public static final long AUTO_RENEW_PERIOD = 8_000_000L;
    public static final String TOKEN_SYMBOL = "tokenSymbol";
    public static final String TOKEN_NAME = "tokenName";
    public static final String MEMO = "memo";
    public static final String TOKEN_CREATE_CONTRACT_AS_KEY = "tokenCreateContractAsKey";
    private static final String ACCOUNT = "account";
    public static final String TOKEN_CREATE_CONTRACT = "TokenCreateContract";
    public static final String FIRST_CREATE_TXN = "firstCreateTxn";
    private static final String ACCOUNT_BALANCE = "ACCOUNT_BALANCE";
    public static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    public static final String ED25519KEY = "ed25519key";
    public static final String ECDSA_KEY = "ecdsa";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    public static final String EXPLICIT_CREATE_RESULT = "Explicit create result is {}";
    private static final String CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION = "createNFTTokenWithKeysAndExpiry";

    public static void main(String... args) {
        new CreatePrecompileV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    // TODO: Fix contract name in TokenCreateContract.sol
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                fungibleTokenCreateHappyPath(),
                nonFungibleTokenCreateHappyPath(),
                fungibleTokenCreateThenQueryAndTransfer(),
                nonFungibleTokenCreateThenQuery(),
                inheritsSenderAutoRenewAccountIfAnyForNftCreate(),
                inheritsSenderAutoRenewAccountForTokenCreate(),
                createTokenWithDefaultExpiryAndEmptyKeys());
    }

    List<HapiSpec> negativeSpecs() {
        // TODO: Where are the security model v1 _negative_ tests?
        return List.of();
    }

    // TEST-001
    final HapiSpec fungibleTokenCreateHappyPath() {
        final var tokenCreateContractAsKeyDelegate = "tokenCreateContractAsKeyDelegate";
        final var createTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("fungibleTokenCreateHappyPath")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        cryptoCreate(ACCOUNT_2).balance(ONE_HUNDRED_HBARS).key(ECDSA_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .signedBy(CONTRACT_ADMIN_KEY, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenWithKeysAndExpiry",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT_TO_ASSOCIATE))))
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)),
                        newKeyNamed(tokenCreateContractAsKeyDelegate)
                                .shape(DELEGATE_CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS),
                                TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS),
                                TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS)),
                        sourcing(() ->
                                getAccountInfo(ACCOUNT_TO_ASSOCIATE).logged().hasTokenRelationShipCount(1)),
                        sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                        .setTokenNum(createTokenNum.get())
                                        .build()))
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(TOKEN_SYMBOL)
                                .hasName(TOKEN_NAME)
                                .hasDecimals(8)
                                .hasTotalSupply(100)
                                .hasEntityMemo(MEMO)
                                .hasTreasury(ACCOUNT)
                                // Token doesn't inherit contract's auto-renew
                                // account if set in tokenCreate
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(ED25519KEY)
                                .hasKycKey(ED25519KEY)
                                .hasFreezeKey(ECDSA_KEY)
                                .hasWipeKey(ECDSA_KEY)
                                .hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasFeeScheduleKey(tokenCreateContractAsKeyDelegate)
                                .hasPauseKey(CONTRACT_ADMIN_KEY)
                                .hasPauseStatus(TokenPauseStatus.Unpaused)),
                        cryptoDelete(ACCOUNT).hasKnownStatus(ACCOUNT_IS_TREASURY));
    }

    // TEST-002

    final HapiSpec inheritsSenderAutoRenewAccountIfAnyForNftCreate() {
        final var createdNftTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("inheritsSenderAutoRenewAccountIfAnyForNftCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(TOKEN_CREATE_CONTRACT)
                                        .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                        .gas(GAS_TO_OFFER))),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .then(withOpContext((spec, ignore) -> {
                    final var subop1 = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
                    final var subop2 = contractCall(
                                    TOKEN_CREATE_CONTRACT,
                                    CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION,
                                    HapiParserUtil.asHeadlongAddress(
                                            asAddress(spec.registry().getAccountID(ACCOUNT))),
                                    spec.registry()
                                            .getKey(ED25519KEY)
                                            .getEd25519()
                                            .toByteArray(),
                                    HapiParserUtil.asHeadlongAddress(new byte[20]),
                                    AUTO_RENEW_PERIOD)
                            .via(FIRST_CREATE_TXN)
                            .gas(GAS_TO_OFFER)
                            .payingWith(ACCOUNT)
                            .sending(DEFAULT_AMOUNT_TO_SEND)
                            .refusingEthConversion()
                            .exposingResultTo(result -> {
                                log.info("Explicit create result is" + " {}", result[0]);
                                final var res = (Address) result[0];
                                createdNftTokenNum.set(res.value().longValueExact());
                            })
                            .hasKnownStatus(SUCCESS);

                    allRunFor(
                            spec,
                            subop1,
                            subop2,
                            childRecordsCheck(
                                    FIRST_CREATE_TXN,
                                    SUCCESS,
                                    TransactionRecordAsserts.recordWith().status(SUCCESS)));

                    final var nftInfo = getTokenInfo(asTokenString(TokenID.newBuilder()
                                    .setTokenNum(createdNftTokenNum.get())
                                    .build()))
                            .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                            .logged();

                    allRunFor(spec, nftInfo);
                }));
    }

    final HapiSpec inheritsSenderAutoRenewAccountForTokenCreate() {
        final var createTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("inheritsSenderAutoRenewAccountForTokenCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT) // inherits if the tokenCreateOp doesn't
                                // have
                                // autoRenewAccount
                                .signedBy(CONTRACT_ADMIN_KEY, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenWithKeysAndExpiry",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(new byte[20]), // set empty
                                        // autoRenewAccount
                                        AUTO_RENEW_PERIOD,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT_TO_ASSOCIATE))))
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }))))
                .then(sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                .setTokenNum(createTokenNum.get())
                                .build()))
                        .logged()
                        .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .hasPauseStatus(TokenPauseStatus.Unpaused)));
    }

    // TEST-003 & TEST-019
    final HapiSpec nonFungibleTokenCreateHappyPath() {
        final var createdTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("nonFungibleTokenCreateHappyPath")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER))
                .when(withOpContext((spec, opLog) ->
                        allRunFor(spec, contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))))
                .then(withOpContext((spec, ignore) -> {
                    final var subop1 = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
                    final var subop2 = contractCall(
                                    TOKEN_CREATE_CONTRACT,
                                    CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION,
                                    HapiParserUtil.asHeadlongAddress(
                                            asAddress(spec.registry().getAccountID(ACCOUNT))),
                                    spec.registry()
                                            .getKey(ED25519KEY)
                                            .getEd25519()
                                            .toByteArray(),
                                    HapiParserUtil.asHeadlongAddress(
                                            asAddress(spec.registry().getAccountID(ACCOUNT))),
                                    AUTO_RENEW_PERIOD)
                            .via(FIRST_CREATE_TXN)
                            .gas(GAS_TO_OFFER)
                            .payingWith(ACCOUNT)
                            .sending(DEFAULT_AMOUNT_TO_SEND)
                            .exposingResultTo(result -> {
                                log.info("Explicit create result is" + " {}", result[0]);
                                final var res = (Address) result[0];
                                createdTokenNum.set(res.value().longValueExact());
                            })
                            .refusingEthConversion()
                            .hasKnownStatus(SUCCESS);
                    final var subop3 = getTxnRecord(FIRST_CREATE_TXN);
                    allRunFor(
                            spec,
                            subop1,
                            subop2,
                            subop3,
                            childRecordsCheck(
                                    FIRST_CREATE_TXN,
                                    SUCCESS,
                                    TransactionRecordAsserts.recordWith().status(SUCCESS)));

                    final var delta = subop3.getResponseRecord().getTransactionFee();
                    final var effectivePayer = ACCOUNT;
                    final var subop4 = getAccountBalance(effectivePayer)
                            .hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta + DEFAULT_AMOUNT_TO_SEND)));
                    final var contractBalanceCheck = getContractInfo(TOKEN_CREATE_CONTRACT)
                            .has(ContractInfoAsserts.contractWith()
                                    .balanceGreaterThan(0L)
                                    .balanceLessThan(DEFAULT_AMOUNT_TO_SEND));
                    final var getAccountTokenBalance = getAccountBalance(ACCOUNT)
                            .hasTokenBalance(
                                    asTokenString(TokenID.newBuilder()
                                            .setTokenNum(createdTokenNum.get())
                                            .build()),
                                    0);
                    final var tokenInfo = getTokenInfo(asTokenString(TokenID.newBuilder()
                                    .setTokenNum(createdTokenNum.get())
                                    .build()))
                            .hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                            .hasSymbol(TOKEN_SYMBOL)
                            .hasName(TOKEN_NAME)
                            .hasDecimals(0)
                            .hasTotalSupply(0)
                            .hasEntityMemo(MEMO)
                            .hasTreasury(ACCOUNT)
                            .hasAutoRenewAccount(ACCOUNT)
                            .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                            .hasSupplyType(TokenSupplyType.FINITE)
                            .hasFreezeDefault(TokenFreezeStatus.Frozen)
                            .hasMaxSupply(10)
                            .searchKeysGlobally()
                            .hasAdminKey(ED25519KEY)
                            .hasSupplyKey(ED25519KEY)
                            .hasPauseKey(ED25519KEY)
                            .hasFreezeKey(ED25519KEY)
                            .hasKycKey(ED25519KEY)
                            .hasFeeScheduleKey(ED25519KEY)
                            .hasWipeKey(ED25519KEY)
                            .hasPauseStatus(TokenPauseStatus.Unpaused)
                            .logged();
                    allRunFor(spec, subop4, getAccountTokenBalance, tokenInfo, contractBalanceCheck);
                }));
    }

    // TEST-005
    final HapiSpec fungibleTokenCreateThenQueryAndTransfer() {
        final var createdTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("fungibleTokenCreateThenQueryAndTransfer")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        cryptoCreate(ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(ED25519KEY)
                                .maxAutomaticTokenAssociations(1),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenThenQueryAndTransfer",
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createdTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS)),
                        sourcing(() -> getAccountBalance(ACCOUNT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createdTokenNum.get())
                                                .build()),
                                        20)),
                        sourcing(() -> getAccountBalance(TOKEN_CREATE_CONTRACT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createdTokenNum.get())
                                                .build()),
                                        10)),
                        sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                        .setTokenNum(createdTokenNum.get())
                                        .build()))
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(TOKEN_SYMBOL)
                                .hasName(TOKEN_NAME)
                                .hasDecimals(8)
                                .hasTotalSupply(30)
                                .hasEntityMemo(MEMO)
                                .hasTreasury(TOKEN_CREATE_CONTRACT)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(ED25519KEY)
                                .hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasPauseKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasPauseStatus(TokenPauseStatus.Unpaused)
                                .logged()));
    }

    // TEST-006
    final HapiSpec nonFungibleTokenCreateThenQuery() {
        final var createdTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("nonFungibleTokenCreateThenQuery")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createNonFungibleTokenThenQuery",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createdTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS)),
                        sourcing(() -> getAccountBalance(TOKEN_CREATE_CONTRACT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createdTokenNum.get())
                                                .build()),
                                        0)),
                        sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                        .setTokenNum(createdTokenNum.get())
                                        .build()))
                                .hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .hasSymbol(TOKEN_SYMBOL)
                                .hasName(TOKEN_NAME)
                                .hasDecimals(0)
                                .hasTotalSupply(0)
                                .hasEntityMemo(MEMO)
                                .hasTreasury(TOKEN_CREATE_CONTRACT)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
                                .logged()));
    }

    final HapiSpec createTokenWithDefaultExpiryAndEmptyKeys() {
        final var tokenCreateContractAsKeyDelegate = "createTokenWithDefaultExpiryAndEmptyKeys";
        final var createTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("createTokenWithDefaultExpiryAndEmptyKeys")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        cryptoCreate(ACCOUNT_2).balance(ONE_HUNDRED_HBARS).key(ECDSA_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .signedBy(CONTRACT_ADMIN_KEY, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(TOKEN_CREATE_CONTRACT, tokenCreateContractAsKeyDelegate)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)),
                        newKeyNamed(tokenCreateContractAsKeyDelegate)
                                .shape(DELEGATE_CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

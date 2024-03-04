/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getNestedContractAddress;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class AssociatePrecompileV2SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(AssociatePrecompileV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String SIGNER = "anybody";
    private static final String TOKEN_TREASURY = "treasury";
    public static final String ASSOCIATE_CONTRACT = "AssociateDissociate";
    public static final String NESTED_ASSOCIATE_CONTRACT = "NestedAssociateDissociate";
    private static final KeyShape THRESHOLD_KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, ED25519, DELEGATE_CONTRACT);
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String ACCOUNT = "anybody";
    private static final String FROZEN_TOKEN = "Frozen token";
    private static final String UNFROZEN_TOKEN = "Unfrozen token";
    private static final String KYC_TOKEN = "KYC token";
    private static final String FREEZE_KEY = "Freeze key";
    private static final String KYC_KEY = "KYC key";
    private static final String ADMIN_KEY = "Admin key";
    private static final String CONTRACT_KEY = "ContractKey";
    private static final String MINT_TOKEN_CONTRACT = "MixedMintToken";

    public static void main(String... args) {
        new AssociatePrecompileV2SecurityModelSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                v2Security006TokenAssociateNegativeTests(), V2Security041TokenAssociateFromStaticcallAndCallcode());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                v2Security031AssociateSingleTokenWithDelegateContractKey(),
                v2Security010NestedAssociateNftAndNonFungibleTokens(),
                V2Security036TokenAssociateFromDelegateCallWithDelegateContractId());
    }

    @HapiTest
    final HapiSpec v2Security031AssociateSingleTokenWithDelegateContractKey() {

        return defaultHapiSpec("v2Security031AssociateSingleTokenWithDelegateContractKey")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        tokenCreate(FROZEN_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .freezeKey(FREEZE_KEY)
                                .freezeDefault(true)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        tokenCreate(UNFROZEN_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .freezeDefault(false)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        tokenCreate(KYC_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        uploadInitCode(ASSOCIATE_CONTRACT, MINT_TOKEN_CONTRACT),
                        contractCreate(MINT_TOKEN_CONTRACT),
                        contractCreate(ASSOCIATE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(CONTRACT_KEY).shape(THRESHOLD_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_CONTRACT))),
                        cryptoUpdate(SIGNER).key(CONTRACT_KEY),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        // Test Case 1: Account paying and signing a fungible TOKEN ASSOCIATE TRANSACTION,
                        // when signer has a threshold key
                        // associating ACCOUNT to the token
                        // SIGNER → call → CONTRACT A → call → HTS
                        contractCall(
                                        ASSOCIATE_CONTRACT,
                                        "tokenAssociate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("fungibleTokenAssociate")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        // Test Case 2: Account paying and signing a non fungible TOKEN ASSOCIATE TRANSACTION,
                        // when signer has a threshold key
                        // associating ACCOUNT to the token
                        // SIGNER → call → CONTRACT A → call → HTS
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        contractCall(
                                        ASSOCIATE_CONTRACT,
                                        "tokenAssociate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("nonFungibleTokenAssociate")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        // Test Case 3: Account paying and signing a multiple TOKENS ASSOCIATE TRANSACTION,
                        // when signer has a threshold key
                        // SIGNER → call → CONTRACT A → call → HTS
                        tokenUpdate(FROZEN_TOKEN).supplyKey(CONTRACT_KEY),
                        tokenUpdate(UNFROZEN_TOKEN).supplyKey(CONTRACT_KEY),
                        tokenUpdate(KYC_TOKEN).supplyKey(CONTRACT_KEY),
                        contractCall(
                                        ASSOCIATE_CONTRACT,
                                        "tokensAssociate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        new Address[] {
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FROZEN_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(UNFROZEN_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(KYC_TOKEN))),
                                        })
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("multipleTokensAssociate")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))))
                .then(getAccountInfo(ACCOUNT)
                        .hasToken(relationshipWith(FUNGIBLE_TOKEN)
                                .kyc(KycNotApplicable)
                                .freeze(FreezeNotApplicable))
                        .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN)
                                .kyc(KycNotApplicable)
                                .freeze(FreezeNotApplicable))
                        .hasToken(relationshipWith(FROZEN_TOKEN)
                                .kyc(KycNotApplicable)
                                .freeze(Frozen))
                        .hasToken(relationshipWith(UNFROZEN_TOKEN)
                                .kyc(KycNotApplicable)
                                .freeze(Unfrozen))
                        .hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable)));
    }

    @HapiTest
    final HapiSpec v2Security006TokenAssociateNegativeTests() {
        return defaultHapiSpec("v2Security006TokenAssociateNegativeTests")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(ADMIN_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SIGNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(ADMIN_KEY)
                                .supplyKey(TOKEN_TREASURY),
                        tokenCreate(FROZEN_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .freezeKey(FREEZE_KEY)
                                .freezeDefault(true)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        tokenCreate(UNFROZEN_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .freezeDefault(false)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        tokenCreate(KYC_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        uploadInitCode(ASSOCIATE_CONTRACT, NESTED_ASSOCIATE_CONTRACT, MINT_TOKEN_CONTRACT),
                        contractCreate(ASSOCIATE_CONTRACT),
                        contractCreate(MINT_TOKEN_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                NESTED_ASSOCIATE_CONTRACT,
                                asHeadlongAddress(getNestedContractAddress(ASSOCIATE_CONTRACT, spec))),
                        // Test Case 1: SIGNER account  paying and signing a fungible TOKEN ASSOCIATE TRANSACTION,
                        // associating token to ACCOUNT
                        // SIGNER → call → CONTRACT A → call → HTS
                        contractCall(
                                        ASSOCIATE_CONTRACT,
                                        "tokenAssociate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .signedBy(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("fungibleTokenAssociate")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord("fungibleTokenAssociate")
                                .andAllChildRecords()
                                .logged(),
                        // Test Case 2: SIGNER account  paying and signing a non fungible TOKEN ASSOCIATE TRANSACTION,
                        // associating to ACCOUNT
                        // SIGNER → call → CONTRACT A → call → HTS
                        contractCall(
                                        ASSOCIATE_CONTRACT,
                                        "tokenAssociate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .signedBy(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("nonFungibleTokenAssociate")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord("nonFungibleTokenAssociate")
                                .andAllChildRecords()
                                .logged(),
                        // Test Case 3: SIGNER account  paying and signing multiple TOKENS ASSOCIATE TRANSACTION,
                        // associating to ЕОА ACCOUNT
                        // SIGNER → call → CONTRACT A → call → HTS
                        contractCall(
                                        ASSOCIATE_CONTRACT,
                                        "tokensAssociate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        new Address[] {
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FROZEN_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(UNFROZEN_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(KYC_TOKEN)))
                                        })
                                .signedBy(ACCOUNT)
                                .payingWith(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("multipleTokensAssociate")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord("multipleTokensAssociate")
                                .andAllChildRecords()
                                .logged(),
                        // Test Case 4: SIGNER account  paying and signing nested TOKEN ASSOCIATE TRANSACTION,
                        // associating to ЕОА ACCOUNT
                        // SIGNER → call → CONTRACT A → call → HTS
                        contractCall(
                                        NESTED_ASSOCIATE_CONTRACT,
                                        "associateInternalContractCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .signedBy(ACCOUNT)
                                .payingWith(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("nestedAssociateFungibleTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord("nestedAssociateFungibleTxn")
                                .andAllChildRecords()
                                .logged(),
                        // Test Case 5: SIGNER account paying and signing a fungible TOKEN ASSOCIATE TRANSACTION,
                        // associating to CONTRACT
                        // when signer has a threshold key
                        // SIGNER → call → CONTRACT A → call → HTS
                        newKeyNamed(CONTRACT_KEY).shape(THRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MINT_TOKEN_CONTRACT))),
                        cryptoUpdate(SIGNER).key(CONTRACT_KEY),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        contractCall(
                                        ASSOCIATE_CONTRACT,
                                        "tokenAssociate",
                                        asHeadlongAddress(getNestedContractAddress(MINT_TOKEN_CONTRACT, spec)),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("associateTokenToContractFails")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord("associateTokenToContractFails")
                                .andAllChildRecords()
                                .logged())))
                .then(
                        childRecordsCheck(
                                "fungibleTokenAssociate",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                "nonFungibleTokenAssociate",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                "multipleTokensAssociate",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                "nestedAssociateFungibleTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                "associateTokenToContractFails",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))));
    }

    @HapiTest
    final HapiSpec v2Security010NestedAssociateNftAndNonFungibleTokens() {

        return defaultHapiSpec("v2Security010NestedAssociateNftAndNonFungibleTokens")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(TOKEN_TREASURY)
                                .initialSupply(0)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(ASSOCIATE_CONTRACT, NESTED_ASSOCIATE_CONTRACT),
                        contractCreate(ASSOCIATE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                NESTED_ASSOCIATE_CONTRACT,
                                asHeadlongAddress(getNestedContractAddress(ASSOCIATE_CONTRACT, spec))),
                        // Test Case 1: Account paying and signing a nested fungible TOKEN ASSOCIATE TRANSACTION,
                        // when we associate the token to the signer
                        // SIGNER → call → CONTRACT A → call → CONTRACT B → call → PRECOMPILE(HTS)
                        newKeyNamed(CONTRACT_KEY).shape(THRESHOLD_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        NESTED_ASSOCIATE_CONTRACT,
                                        "associateInternalContractCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .signedBy(ACCOUNT)
                                .payingWith(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("nestedAssociateFungibleTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord("nestedAssociateFungibleTxn")
                                .andAllChildRecords()
                                .logged(),
                        // Test Case 2: Account paying and signing a nested non fungible TOKEN ASSOCIATE TRANSACTION,
                        // when we associate the token to the signer
                        // SIGNER → call → CONTRACT A → call → CONTRACT B → call → PRECOMPILE(HTS)
                        contractCall(
                                        NESTED_ASSOCIATE_CONTRACT,
                                        "associateInternalContractCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .signedBy(ACCOUNT)
                                .payingWith(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("nestedAssociateNonFungibleTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        getAccountInfo(ACCOUNT)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN)
                                        .kyc(KycNotApplicable)
                                        .freeze(FreezeNotApplicable))
                                .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN)
                                        .kyc(KycNotApplicable)
                                        .freeze(FreezeNotApplicable)),
                        childRecordsCheck(
                                "nestedAssociateFungibleTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "nestedAssociateNonFungibleTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))));
    }

    @HapiTest
    final HapiSpec V2Security036TokenAssociateFromDelegateCallWithDelegateContractId() {

        return defaultHapiSpec("v2Security010NestedAssociateNftAndNonFungibleTokens")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(TOKEN_TREASURY)
                                .initialSupply(0)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(ASSOCIATE_CONTRACT, NESTED_ASSOCIATE_CONTRACT),
                        contractCreate(ASSOCIATE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                NESTED_ASSOCIATE_CONTRACT,
                                asHeadlongAddress(getNestedContractAddress(ASSOCIATE_CONTRACT, spec))),
                        // SIGNER → call → CONTRACT A → delegatecall → CONTRACT B → call → PRECOMPILE(HTS)
                        newKeyNamed(CONTRACT_KEY)
                                .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, NESTED_ASSOCIATE_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        NESTED_ASSOCIATE_CONTRACT,
                                        "associateDelegateCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .signedBy(ACCOUNT)
                                .payingWith(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("nestedAssociateFungibleTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord("nestedAssociateFungibleTxn")
                                .andAllChildRecords()
                                .logged(),
                        // non fungible token
                        contractCall(
                                        NESTED_ASSOCIATE_CONTRACT,
                                        "associateDelegateCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .signedBy(ACCOUNT)
                                .payingWith(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("nestedAssociateNonFungibleTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord("nestedAssociateNonFungibleTxn")
                                .andAllChildRecords()
                                .logged())))
                .then(
                        getAccountInfo(ACCOUNT)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN)
                                        .kyc(KycNotApplicable)
                                        .freeze(FreezeNotApplicable))
                                .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN)
                                        .kyc(KycNotApplicable)
                                        .freeze(FreezeNotApplicable)),
                        childRecordsCheck(
                                "nestedAssociateFungibleTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "nestedAssociateNonFungibleTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))));
    }

    @HapiTest
    final HapiSpec V2Security041TokenAssociateFromStaticcallAndCallcode() {

        return defaultHapiSpec("V2Security041TokenAssociateFromStaticcallAndCallcode")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(TOKEN_TREASURY)
                                .initialSupply(0)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(ASSOCIATE_CONTRACT, NESTED_ASSOCIATE_CONTRACT),
                        contractCreate(ASSOCIATE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                NESTED_ASSOCIATE_CONTRACT,
                                asHeadlongAddress(getNestedContractAddress(ASSOCIATE_CONTRACT, spec))),
                        // SIGNER → call → CONTRACT A → staticcall → CONTRACT B → call → PRECOMPILE(HTS)
                        newKeyNamed(CONTRACT_KEY)
                                .shape(THRESHOLD_KEY_SHAPE.signedWith(sigs(ON, NESTED_ASSOCIATE_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        NESTED_ASSOCIATE_CONTRACT,
                                        "associateStaticCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .signedBy(ACCOUNT)
                                .payingWith(ACCOUNT)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("associateStaticcallFungibleTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord("associateStaticcallFungibleTxn")
                                .andAllChildRecords()
                                .logged())))
                .then(
                        emptyChildRecordsCheck("associateStaticcallFungibleTxn", CONTRACT_REVERT_EXECUTED),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(FUNGIBLE_TOKEN));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

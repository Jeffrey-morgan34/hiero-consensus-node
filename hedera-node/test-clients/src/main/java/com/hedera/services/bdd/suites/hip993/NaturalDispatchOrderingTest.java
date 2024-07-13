/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip993;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.operations.transactions.TouchBalancesOperation.touchBalanceOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustIncludeNoFailuresFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateVisibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleNonSyntheticItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.hip904.UnlimitedAutoAssociationSuite.UNLIMITED_AUTO_ASSOCIATION_SLOTS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsAssertion;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

/**
 * Asserts the expected presence and order of all valid combinations of preceding and following stream items;
 * both when rolled back and directly committed. It particularly emphasizes the natural ordering of
 * {@link TransactionCategory#PRECEDING} stream items as defined in
 * [HIP-993](<a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/a64bdb258d52ba4ce1ca26bede8e03871b9ade10/HIP/hip-993.md#natural-ordering-of-preceding-records">...</a>).
 * <p>
 * The only stream items created in a savepoint that are <b>not</b> expected to be present are those with reversing
 * behavior {@link SingleTransactionRecordBuilder.ReversingBehavior#REMOVABLE}, and whose originating savepoint was rolled back.
 * <p>
 * All other stream items are expected to be present in the record stream once created; but if they are
 * {@link SingleTransactionRecordBuilder.ReversingBehavior#REVERSIBLE}, their status may be changed from {@code SUCCESS} to
 * {@code REVERTED_SUCCESS} when their originating savepoint is rolled back.
 * <p>
 * Only {@link SingleTransactionRecordBuilder.ReversingBehavior#IRREVERSIBLE} streams items appear unchanged in the record stream no matter whether
 * their originating savepoint is rolled back.
 */
@DisplayName("given HIP-993 natural dispatch ordering")
public class NaturalDispatchOrderingTest {
    /**
     * Tests the {@link TransactionCategory#USER} + {@link ReversingBehavior#REVERSIBLE} combination for
     * both commit and rollback via,
     * <ol>
     *     <Li>Successfully creating a scheduled transaction.</Li>
     *     <Li>Failing to create an identical schedule transaction, which leaves some information in the receipt
     *     despite rolling back the root savepoint stack.</Li>
     * </ol>
     * @return the test
     */
    @HapiTest
    @DisplayName("reversible user stream items are as expected")
    final Stream<DynamicTest> reversibleUserStreamItemsAsExpected() {
        final AtomicReference<VisibleItemsAssertion> assertion = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(
                        visibleNonSyntheticItems(assertion, "firstCreation", "duplicateCreation")),
                scheduleCreate(
                                "scheduledTxn",
                                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
                                        .fee(ONE_HBAR))
                        .via("firstCreation"),
                scheduleCreate(
                                "duplicateScheduledTxn",
                                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
                                        .fee(ONE_HBAR))
                        .via("duplicateCreation")
                        .hasKnownStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED),
                validateVisibleItems(assertion, reversibleUserValidator()));
    }

    /**
     * Tests the {@link TransactionCategory#CHILD} + {@link ReversingBehavior#REVERSIBLE} combination as
     * well as the {@link TransactionCategory#PRECEDING} + {@link ReversingBehavior#REMOVABLE} combination for
     * both commit and rollback via,
     * <ol>
     *     <Li>Successfully calling a transfer system contract that does an auto-association in a frame that doesn't
     *     revert, within an EVM transaction that doesn't revert.</Li>
     *     <Li>Calling a transfer system contract that does an auto-association in a frame that reverts, within an
     *     EVM transaction that doesn't revert.</Li>
     *     <Li>Calling a transfer system contract that does an auto-association in a frame that succeeds, but within
     *     an EVM transaction that reverts.</Li>
     * </ol>
     * @return the test
     */
    @HapiTest
    @DisplayName("reversible child and removable preceding stream items are as expected")
    final Stream<DynamicTest> reversibleChildAndRemovablePrecedingStreamItemsAsExpected(
            @NonFungibleToken(numPreMints = 2) SpecNonFungibleToken nonFungibleToken,
            @Account(autoAssociationSlots = 1) SpecAccount beneficiary,
            @Contract(contract = "PrecompileAliasXfer", creationGas = 2_000_000) SpecContract transferContract,
            @Contract(contract = "LowLevelCall") SpecContract lowLevelCallContract) {
        final var transferFunction = new Function("transferNFTThanRevertCall(address,address,address,int64)");
        final AtomicReference<VisibleItemsAssertion> assertion = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(
                        visibleNonSyntheticItems(assertion, "fullSuccess", "containedRevert", "fullRevert")),
                nonFungibleToken.treasury().authorizeContract(transferContract),
                transferContract
                        .call("transferNFTCall", nonFungibleToken, nonFungibleToken.treasury(), beneficiary, 1L)
                        .andAssert(txn -> txn.via("fullSuccess")),
                withOpContext((spec, opLog) -> {
                    final var calldata = transferFunction.encodeCallWithArgs(
                            nonFungibleToken.addressOn(spec.targetNetworkOrThrow()),
                            nonFungibleToken.treasury().addressOn(spec.targetNetworkOrThrow()),
                            beneficiary.addressOn(spec.targetNetworkOrThrow()),
                            2L);
                    allRunFor(
                            spec,
                            lowLevelCallContract
                                    .call(
                                            "callRequestedAndIgnoreFailure",
                                            transferContract,
                                            calldata.array(),
                                            BigInteger.valueOf(1_000_000))
                                    .andAssert(txn -> txn.gas(2_000_000).via("containedRevert")));
                }),
                transferContract
                        .call(
                                "transferNFTThanRevertCall",
                                nonFungibleToken,
                                nonFungibleToken.treasury(),
                                beneficiary,
                                2L)
                        .andAssert(txn -> txn.via("fullRevert").hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                nonFungibleToken.serialNo(2L).assertOwnerIs(nonFungibleToken.treasury()),
                validateVisibleItems(assertion, reversibleChildValidator()));
    }

    /**
     * Tests the {@link TransactionCategory#SCHEDULED} + {@link ReversingBehavior#REVERSIBLE} combination as
     * well as the {@link TransactionCategory#PRECEDING} + {@link ReversingBehavior#REMOVABLE} combination for
     * both commit and rollback via,
     * <ol>
     *     <Li>Successfully creating an immediately triggered transfer that does two auto-associations with
     *     a payer that can afford all the fees.</Li>
     *     <Li>Successfully creating an immediately triggered transfer that does two auto-associations with
     *     a payer that cannot afford all the fees, and hence rolls back its first preceding child due to
     *     a {@link ResponseCodeEnum#INSUFFICIENT_PAYER_BALANCE} result on the second auto-association.</Li>
     * </ol>
     * @return the test
     */
    @HapiTest
    @DisplayName(
            "reversible schedule stream items and removable preceding stream items are as expected are as expected")
    final Stream<DynamicTest> reversibleScheduleStreamAndRemovablePrecedingItemsAsExpected(
            @FungibleToken SpecFungibleToken firstToken,
            @FungibleToken SpecFungibleToken secondToken,
            @Account(autoAssociationSlots = 2) SpecAccount firstReceiver,
            @Account(autoAssociationSlots = 2) SpecAccount secondReceiver,
            @Account(centBalance = 100, autoAssociationSlots = UNLIMITED_AUTO_ASSOCIATION_SLOTS)
                    SpecAccount solventPayer,
            @Account(centBalance = 7, autoAssociationSlots = UNLIMITED_AUTO_ASSOCIATION_SLOTS)
                    SpecAccount insolventPayer) {
        final AtomicReference<VisibleItemsAssertion> assertion = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(visibleNonSyntheticItems(assertion, "committed", "rolledBack")),
                firstToken.treasury().transferUnitsTo(solventPayer, 10, firstToken),
                secondToken.treasury().transferUnitsTo(insolventPayer, 10, secondToken),
                // Ensure the receiver entities exist before switching out object-oriented DSL
                touchBalanceOf(firstReceiver, secondReceiver),
                // Immediate trigger a schedule dispatch that succeeds as payer can afford two auto-associations
                scheduleCreate(
                                "committedTxn",
                                cryptoTransfer(moving(2, firstToken.name())
                                                .distributing(
                                                        solventPayer.name(),
                                                        firstReceiver.name(),
                                                        secondReceiver.name()))
                                        .fee(ONE_HBAR / 10))
                        .designatingPayer(solventPayer.name())
                        .alsoSigningWith(solventPayer.name())
                        .via("committed"),
                // Immediate trigger a schedule dispatch that rolls back as payer cannot afford two auto-associations
                scheduleCreate(
                                "rolledBackTxn",
                                cryptoTransfer(moving(2, secondToken.name())
                                                .distributing(
                                                        insolventPayer.name(),
                                                        firstReceiver.name(),
                                                        secondReceiver.name()))
                                        .fee(ONE_HBAR / 10))
                        .designatingPayer(insolventPayer.name())
                        .alsoSigningWith(insolventPayer.name())
                        .via("rolledBack"),
                validateVisibleItems(assertion, reversibleScheduleValidator()));
    }

    private static BiConsumer<HapiSpec, Map<String, List<RecordStreamEntry>>> reversibleUserValidator() {
        return (spec, records) -> {
            final var successItems = records.get("firstCreation");
            assertScheduledItemsMatch(successItems, 0, 1, ScheduleCreate, CryptoTransfer);
            final var duplicateItems = records.get("duplicateCreation");
            assertScheduledItemsMatch(duplicateItems, 0, -1, ScheduleCreate);
            final var creation = successItems.getFirst();
            final var duplicate = duplicateItems.getFirst();
            assertEquals(creation.createdScheduleId(), duplicate.createdScheduleId());
            assertEquals(creation.scheduledTransactionId(), duplicate.scheduledTransactionId());
        };
    }

    private static BiConsumer<HapiSpec, Map<String, List<RecordStreamEntry>>> reversibleScheduleValidator() {
        return (spec, records) -> {
            final var committedItems = records.get("committed");
            assertScheduledItemsMatch(
                    committedItems,
                    0,
                    3,
                    ScheduleCreate,
                    TokenAssociateToAccount,
                    TokenAssociateToAccount,
                    CryptoTransfer);
            assertStatuses(committedItems, SUCCESS, SUCCESS, SUCCESS, SUCCESS);
            final var rolledBackItems = records.get("rolledBack");
            assertScheduledItemsMatch(rolledBackItems, 0, 1, ScheduleCreate, CryptoTransfer);
            assertStatuses(rolledBackItems, SUCCESS, INSUFFICIENT_PAYER_BALANCE);
        };
    }

    private static BiConsumer<HapiSpec, Map<String, List<RecordStreamEntry>>> reversibleChildValidator() {
        return (spec, records) -> {
            final var successItems = records.get("fullSuccess");
            assertItemsMatch(successItems, 0, ContractCall, TokenAssociateToAccount, CryptoTransfer);
            assertStatuses(successItems, SUCCESS, SUCCESS, SUCCESS);
            final var containedRevert = records.get("containedRevert");
            System.out.println(containedRevert);
            assertItemsMatch(containedRevert, 0, ContractCall, CryptoTransfer);
            assertStatuses(containedRevert, SUCCESS, REVERTED_SUCCESS);
            final var fullRevert = records.get("fullRevert");
            assertItemsMatch(fullRevert, 0, ContractCall, CryptoTransfer);
            assertStatuses(fullRevert, CONTRACT_REVERT_EXECUTED, REVERTED_SUCCESS);
        };
    }

    private static void assertScheduledItemsMatch(
            @NonNull final List<RecordStreamEntry> items,
            final int userTxnIndex,
            final int triggeredTxnIndex,
            @NonNull final HederaFunctionality... functions) {
        assertEquals(
                functions.length, items.size(), "Expected " + functions.length + " items, but got " + items.size());
        assertArrayEquals(
                items.stream().map(RecordStreamEntry::function).toArray(HederaFunctionality[]::new), functions);
        assertParentChildStructure(items, userTxnIndex, triggeredTxnIndex);
    }

    private static void assertItemsMatch(
            @NonNull final List<RecordStreamEntry> items,
            final int userTxnIndex,
            @NonNull final HederaFunctionality... functions) {
        assertEquals(
                functions.length, items.size(), "Expected " + functions.length + " items, but got " + items.size());
        assertArrayEquals(
                items.stream().map(RecordStreamEntry::function).toArray(HederaFunctionality[]::new), functions);
        assertParentChildStructure(items, userTxnIndex, -1);
    }

    private static void assertStatuses(
            @NonNull final List<RecordStreamEntry> items,
            @NonNull final com.hederahashgraph.api.proto.java.ResponseCodeEnum... expected) {
        final var actual = items.stream().map(RecordStreamEntry::finalStatus).toArray(ResponseCodeEnum[]::new);
        assertArrayEquals(expected, actual);
    }

    /**
     * Asserts the given stream items, which should all be for a single user transaction, have the expected:
     * <ol>
     *     <li>Consensus time offsets</li>
     *     <li>Nonces</li>
     *     <li>Following child parent consensus times</li>
     * </ol>
     * @param items the items for the user transaction
     * @param numPrecedingChildren the number of preceding children expected
     * @param triggeredTxnIndex if not -1, the index of the triggered transaction
     */
    private static void assertParentChildStructure(
            @NonNull final List<RecordStreamEntry> items, final int numPrecedingChildren, final int triggeredTxnIndex) {
        final var userConsensusTime = items.get(numPrecedingChildren).consensusTime();
        final var userTransactionID = items.get(numPrecedingChildren).txnId();
        int nextExpectedNonce = 1;
        for (int i = 0; i < numPrecedingChildren; i++) {
            final var preceding = items.get(i);
            assertEquals(userConsensusTime.minusNanos(i), preceding.consensusTime());
            assertEquals(withNonce(userTransactionID, nextExpectedNonce++), preceding.txnId());
        }
        int postTriggeredOffset = 0;
        for (int i = numPrecedingChildren + 1; i < items.size(); i++) {
            final var following = items.get(i);
            assertEquals(userConsensusTime.plusNanos(i - numPrecedingChildren), following.consensusTime());
            if (i == triggeredTxnIndex) {
                assertEquals(withScheduled(userTransactionID), following.txnId());
                postTriggeredOffset++;
            } else {
                assertEquals(
                        withNonce(userTransactionID, nextExpectedNonce++ - postTriggeredOffset), following.txnId());
                assertEquals(userConsensusTime, following.parentConsensusTimestamp());
            }
        }
    }

    private static TransactionID withNonce(@NonNull final TransactionID parentId, final int nonce) {
        return TransactionID.newBuilder()
                .setAccountID(parentId.getAccountID())
                .setTransactionValidStart(parentId.getTransactionValidStart())
                .setNonce(nonce)
                .build();
    }

    private static TransactionID withScheduled(@NonNull final TransactionID parentId) {
        return TransactionID.newBuilder()
                .setAccountID(parentId.getAccountID())
                .setTransactionValidStart(parentId.getTransactionValidStart())
                .setScheduled(true)
                .build();
    }
}

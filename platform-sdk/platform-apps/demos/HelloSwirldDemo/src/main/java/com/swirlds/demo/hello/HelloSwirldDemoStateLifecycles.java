// SPDX-License-Identifier: Apache-2.0
package com.swirlds.demo.hello;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class handles the lifecycle events for the {@link HelloSwirldDemoState}.
 */
public class HelloSwirldDemoStateLifecycles implements StateLifecycles<HelloSwirldDemoState> {

    private static final Logger logger = LogManager.getLogger(HelloSwirldDemoStateLifecycles.class);

    @Override
    public void onHandleConsensusRound(
            @NonNull final Round round,
            @NonNull final HelloSwirldDemoState state,
            @NonNull
                    final Consumer<ScopedSystemTransaction<StateSignatureTransaction>>
                            stateSignatureTransactionCallback) {
        state.throwIfImmutable();
        round.forEachEventTransaction((event, transaction) -> {
            if (transaction.isSystem()) {
                return;
            } else if (areTransactionBytesSystemOnes(transaction)) {
                consumeSystemTransaction(transaction, event, stateSignatureTransactionCallback);
            }

            handleTransaction(transaction, state);
        });
    }

    private void handleTransaction(final Transaction transaction, HelloSwirldDemoState state) {
        state.getStrings()
                .add(new String(transaction.getApplicationTransaction().toByteArray(), StandardCharsets.UTF_8));
    }

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull HelloSwirldDemoState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        event.forEachTransaction(t -> {
            if (areTransactionBytesSystemOnes(t)) {
                consumeSystemTransaction(t, event, stateSignatureTransactionCallback);
            }
        });
    }

    @Override
    public boolean onSealConsensusRound(@NonNull Round round, @NonNull HelloSwirldDemoState state) {
        // no-op
        return true;
    }

    @Override
    public void onStateInitialized(
            @NonNull HelloSwirldDemoState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        // no-op
    }

    @Override
    public void onUpdateWeight(
            @NonNull HelloSwirldDemoState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull HelloSwirldDemoState recoveredState) {
        // no-op// no-op
    }

    private void consumeSystemTransaction(
            final Transaction transaction,
            final Event event,
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        try {
            final var stateSignatureTransaction =
                    StateSignatureTransaction.PROTOBUF.parse(transaction.getApplicationTransaction());
            stateSignatureTransactionCallback.accept(new ScopedSystemTransaction<>(
                    event.getCreatorId(), event.getSoftwareVersion(), stateSignatureTransaction));
        } catch (final ParseException e) {
            logger.error("Failed to parse StateSignatureTransaction", e);
        }
    }

    /**
     * Checks if the transaction bytes are system ones. The test creates application transactions
     * with a value generated by {@link RosterUtils}. System transactions will be bigger than that.
     *
     * @param transaction the consensus transaction to check
     * @return true if the transaction bytes are system ones, false otherwise
     */
    private boolean areTransactionBytesSystemOnes(final Transaction transaction) {
        final String exampleName = RosterUtils.formatNodeName(Long.MAX_VALUE);

        return transaction.getApplicationTransaction().length() > exampleName.length();
    }
}

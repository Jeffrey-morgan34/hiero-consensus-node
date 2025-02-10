// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.eventhandling;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.metrics.api.Metrics.INTERNAL_CATEGORY;

import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.stats.AverageTimeStat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of the {@link TransactionPrehandler} interface
 */
public class DefaultTransactionPrehandler implements TransactionPrehandler {
    private static final Logger logger = LogManager.getLogger(DefaultTransactionPrehandler.class);

    public static final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> NO_OP_CONSUMER =
            systemTransactions -> {};

    /**
     * A source to get the latest immutable state
     */
    private final Supplier<ReservedSignedState> latestStateSupplier;

    /**
     * Average time spent in to prehandle each individual transaction (in microseconds)
     */
    private final AverageTimeStat preHandleTime;

    private final StateLifecycles stateLifecycles;

    private final Time time;

    /**
     * Constructs a new TransactionPrehandler
     *
     * @param platformContext     the platform context
     * @param latestStateSupplier provides access to the latest immutable state, may return null (implementation detail
     *                            of locking mechanism within the supplier)
     * @param stateLifecycles    the state lifecycles
     */
    public DefaultTransactionPrehandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final Supplier<ReservedSignedState> latestStateSupplier,
            @NonNull StateLifecycles<?> stateLifecycles) {
        this.time = platformContext.getTime();
        this.latestStateSupplier = Objects.requireNonNull(latestStateSupplier);

        preHandleTime = new AverageTimeStat(
                platformContext.getMetrics(),
                ChronoUnit.MICROS,
                INTERNAL_CATEGORY,
                "preHandleMicros",
                "average time it takes to perform preHandle (in microseconds)");
        this.stateLifecycles = stateLifecycles;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Queue<ScopedSystemTransaction<StateSignatureTransaction>> prehandleApplicationTransactions(
            @NonNull final PlatformEvent event) {
        final long startTime = time.nanoTime();
        final Queue<ScopedSystemTransaction<StateSignatureTransaction>> scopedSystemTransactions =
                new ConcurrentLinkedQueue<>();
        final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> consumer = scopedSystemTransactions::add;

        for (final EventTransaction eventTransaction : event.getGossipEvent().eventTransaction()) {
            if (eventTransaction.hasStateSignatureTransaction()) {
                scopedSystemTransactions.add(new ScopedSystemTransaction<>(
                        event.getCreatorId(),
                        event.getSoftwareVersion(),
                        eventTransaction.stateSignatureTransaction()));
            }
        }

        ReservedSignedState latestImmutableState = null;
        try {
            latestImmutableState = latestStateSupplier.get();
            while (latestImmutableState == null) {
                latestImmutableState = latestStateSupplier.get();
            }

            try {
                stateLifecycles.onPreHandle(event, latestImmutableState.get().getState(), consumer);
            } catch (final Throwable t) {
                logger.error(
                        EXCEPTION.getMarker(), "error invoking StateLifecycles.onPreHandle() for event {}", event, t);
            }
        } finally {
            event.signalPrehandleCompletion();
            latestImmutableState.close();

            preHandleTime.update(startTime, time.nanoTime());
        }

        return scopedSystemTransactions;
    }
}

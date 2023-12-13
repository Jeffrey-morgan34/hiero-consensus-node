/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.SignedStateInfo;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateToDiskReason;
import com.swirlds.platform.util.HashLogger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The default implementation of {@link StateManagementComponent}.
 */
public class DefaultStateManagementComponent implements StateManagementComponent {

    private static final Logger logger = LogManager.getLogger(DefaultStateManagementComponent.class);

    /**
     * Signed states are deleted on this background thread.
     */
    private final SignedStateGarbageCollector signedStateGarbageCollector;

    /**
     * Hashes SignedStates.
     */
    private final SignedStateHasher signedStateHasher;

    /**
     * Keeps track of various signed states in various stages of collecting signatures
     */
    private final SignedStateManager signedStateManager;

    /**
     * A logger for hash stream data
     */
    private final HashLogger hashLogger;

    /**
     * Used to track signed state leaks, if enabled
     */
    private final SignedStateSentinel signedStateSentinel;

    private final SavedStateController savedStateController;
    private final Consumer<StateDumpRequest> stateDumpConsumer;
    private final Consumer<ReservedSignedState> stateSigner;

    /**
     * @param platformContext                    the platform context
     * @param threadManager                      manages platform thread resources
     * @param dispatchBuilder                    builds dispatchers. This is deprecated, do not wire new things together
     *                                           with this.
     * @param newLatestCompleteStateConsumer     consumer to invoke when there is a new latest complete signed state
     * @param fatalErrorConsumer                 consumer to invoke when a fatal error has occurred
     * @param savedStateController               controls which states are saved to disk
     * @param stateDumpConsumer                  consumer to invoke when a state is requested to be dumped to disk
     */
    public DefaultStateManagementComponent(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final DispatchBuilder dispatchBuilder,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final SavedStateController savedStateController,
            @NonNull final Consumer<StateDumpRequest> stateDumpConsumer,
            @NonNull final Consumer<ReservedSignedState> stateSigner) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(newLatestCompleteStateConsumer);
        Objects.requireNonNull(fatalErrorConsumer);

        // Various metrics about signed states
        final SignedStateMetrics signedStateMetrics = new SignedStateMetrics(platformContext.getMetrics());
        this.signedStateGarbageCollector = new SignedStateGarbageCollector(threadManager, signedStateMetrics);
        this.signedStateSentinel = new SignedStateSentinel(platformContext, threadManager, Time.getCurrent());
        this.savedStateController = Objects.requireNonNull(savedStateController);
        this.stateDumpConsumer = Objects.requireNonNull(stateDumpConsumer);
        this.stateSigner = Objects.requireNonNull(stateSigner);

        hashLogger =
                new HashLogger(threadManager, platformContext.getConfiguration().getConfigData(StateConfig.class));

        final StateHashedTrigger stateHashedTrigger =
                dispatchBuilder.getDispatcher(this, StateHashedTrigger.class)::dispatch;
        signedStateHasher = new SignedStateHasher(signedStateMetrics, stateHashedTrigger, fatalErrorConsumer);

        signedStateManager = new SignedStateManager(
                platformContext.getConfiguration().getConfigData(StateConfig.class),
                signedStateMetrics,
                newLatestCompleteStateConsumer,
                this::stateHasEnoughSignatures,
                this::stateLacksSignatures);
    }

    /**
     * Handles a signed state that is now complete by saving it to disk, if it should be saved.
     *
     * @param signedState the newly complete signed state
     */
    private void stateHasEnoughSignatures(@NonNull final SignedState signedState) {
        savedStateController.maybeSaveState(signedState);
    }

    /**
     * Handles a signed state that did not collect enough signatures before being ejected from memory.
     *
     * @param signedState the signed state that lacks signatures
     */
    private void stateLacksSignatures(@NonNull final SignedState signedState) {
        savedStateController.maybeSaveState(signedState);
    }

    private void newSignedStateBeingTracked(final SignedState signedState, final SourceOfSignedState source) {
        // When we begin tracking a new signed state, "introduce" the state to the SignedStateFileManager
        if (source == SourceOfSignedState.DISK) {
            savedStateController.registerSignedStateFromDisk(signedState);
        }
        if (source == SourceOfSignedState.RECONNECT) {
            // a state received from reconnect should be saved to disk
            savedStateController.reconnectStateReceived(signedState);
        }

        if (signedState.getState().getHash() != null) {
            hashLogger.logHashes(signedState);
        }
    }

    /**
     * Checks if the signed state's round is older than the round of the latest state in the signed state manager.
     *
     * @param signedState the signed state whose round needs to be compared to the latest state in the signed state
     *                    manager.
     * @return true if the signed state's round is < the round of the latest state in the signed state manager,
     * otherwise false.
     */
    private boolean stateRoundIsTooOld(final SignedState signedState) {
        final long roundOfLatestState = signedStateManager.getLastImmutableStateRound();
        if (signedState.getRound() < roundOfLatestState) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "State received from transactions is in an incorrect order. "
                            + "Latest state is from round {}, provided state is from round {}",
                    roundOfLatestState,
                    signedState.getRound());
            return true;
        }
        return false;
    }

    @Override
    public void newSignedStateFromTransactions(@NonNull final ReservedSignedState signedState) {
        try (signedState) {
            signedState.get().setGarbageCollector(signedStateGarbageCollector);

            if (stateRoundIsTooOld(signedState.get())) {
                return; // do not process older states.
            }
            signedStateHasher.hashState(signedState.get());

            newSignedStateBeingTracked(signedState.get(), SourceOfSignedState.TRANSACTIONS);

            stateSigner.accept(signedState.getAndReserve("signing state from transactions"));

            signedStateManager.addState(signedState.get());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReservedSignedState getLatestImmutableState(@NonNull final String reason) {
        return signedStateManager.getLatestImmutableState(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SignedStateInfo> getSignedStateInfo() {
        return signedStateManager.getSignedStateInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stateToLoad(final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {
        signedState.setGarbageCollector(signedStateGarbageCollector);
        newSignedStateBeingTracked(signedState, sourceOfSignedState);
        signedStateManager.addState(signedState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        signedStateGarbageCollector.start();
        signedStateSentinel.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        signedStateSentinel.stop();
        signedStateGarbageCollector.stop();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ReservedSignedState find(final @NonNull Predicate<SignedState> criteria, @NonNull final String reason) {
        return signedStateManager.find(criteria, reason);
    }

    @Override
    public void dumpLatestImmutableState(@NonNull final StateToDiskReason reason, final boolean blocking) {
        Objects.requireNonNull(reason);

        try (final ReservedSignedState reservedState = signedStateManager.getLatestImmutableState(
                "DefaultStateManagementComponent.dumpLatestImmutableState()")) {

            if (reservedState.isNull()) {
                logger.warn(STATE_TO_DISK.getMarker(), "State dump requested, but no state is available.");
            } else {
                dumpState(reservedState.get(), reason, blocking);
            }
        }
    }

    /**
     * Dump a state to disk out-of-band.
     * <p>
     * Writing a state "out-of-band" means the state is being written for the sake of a human, whether for debug
     * purposes, or because of a fault. States written out-of-band will not be read automatically by the platform, and
     * will not be used as an initial state at boot time.
     * <p>
     * A dumped state will be saved in a subdirectory of the signed states base directory, with the subdirectory being
     * named after the reason the state is being written out-of-band.
     *
     * @param signedState the signed state to write to disk
     * @param reason      the reason why the state is being written out-of-band
     * @param blocking    if true then block until the state has been fully written to disk
     */
    private void dumpState(
            @NonNull final SignedState signedState, @NonNull final StateToDiskReason reason, final boolean blocking) {
        Objects.requireNonNull(signedState);
        Objects.requireNonNull(reason);
        signedState.markAsStateToSave(reason);

        final StateDumpRequest request = StateDumpRequest.create(signedState.reserve("dumping to disk"));

        stateDumpConsumer.accept(request);

        if (blocking) {
            request.waitForFinished().run();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getFirstStateTimestamp() {
        return signedStateManager.getFirstStateTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFirstStateRound() {
        return signedStateManager.getFirstStateRound();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SignedStateManager getSignedStateManager() {
        return signedStateManager;
    }
}

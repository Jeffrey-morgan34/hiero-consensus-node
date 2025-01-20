/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.turtle.runner;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;

/**
 * This class handles the lifecycle events for the {@link TurtleTestingToolState}.
 */
enum TurtleStateLifecycles implements StateLifecycles<TurtleTestingToolState> {
    TURTLE_STATE_LIFECYCLES;

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull TurtleTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        // no op
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull TurtleTestingToolState turtleTestingToolState,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        turtleTestingToolState.state = NonCryptographicHashing.hash64(
                turtleTestingToolState.state,
                round.getRoundNum(),
                round.getConsensusTimestamp().getNano(),
                round.getConsensusTimestamp().getEpochSecond());
    }

    @Override
    public void onSealConsensusRound(@NonNull Round round, @NonNull TurtleTestingToolState state) {
        // no op
    }

    @Override
    public void onStateInitialized(
            @NonNull TurtleTestingToolState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        // no op
    }

    @Override
    public void onUpdateWeight(
            @NonNull TurtleTestingToolState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no op
    }

    @Override
    public void onNewRecoveredState(@NonNull TurtleTestingToolState recoveredState) {
        // no op
    }
}

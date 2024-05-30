/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring;

import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.wiring.components.StateAndRound;
import com.swirlds.wiring.transformers.AdvancedTransformation;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Manages reservations of a signed state contained in a {@link StateAndRound} object, when the StateAndRound needs to
 * be reduced to just the state.
 * <p>
 * The contract for managing reservations across vertexes in the wiring is as follows:
 * <ul>
 *     <li>Each vertex, on input, will receive a state reserved for that vertex</li>
 *     <li>The vertex which should either release that state, or return it</li>
 * </ul>
 * The reserver enforces this contract by reserving the state for each input wire, and then releasing the reservation
 * made for the reserver.
 * <p>
 * For each input wire, {@link #transform(StateAndRound)} will be called once, reserving the state for that input
 * wire. After a reservation is made for each input wire, {@link #inputCleanup(StateAndRound)} will be called once to
 * release the original reservation.
 *
 * @param name the name of the reserver
 */
public record StateAndRoundToStateReserver(@NonNull String name)
        implements AdvancedTransformation<StateAndRound, ReservedSignedState> {

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ReservedSignedState transform(@NonNull final StateAndRound stateAndRound) {
        return stateAndRound.makeAdditionalReservation(name).reservedSignedState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void inputCleanup(@NonNull final StateAndRound stateAndRound) {
        stateAndRound.reservedSignedState().close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void outputCleanup(@NonNull final ReservedSignedState reservedSignedState) {
        reservedSignedState.close();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getTransformerName() {
        return name;
    }

    @NonNull
    @Override
    public String getTransformerInputName() {
        return "round and state to reserve";
    }
}

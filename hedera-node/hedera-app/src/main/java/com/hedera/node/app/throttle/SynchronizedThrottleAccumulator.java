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

package com.hedera.node.app.throttle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.throttle.annotations.IngestThrottle;
import com.hedera.node.app.workflows.TransactionInfo;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Keeps track of the amount of usage of different TPS throttle categories and gas, and returns whether a given
 * transaction or query should be throttled based on that.
 * Meant to be used in multithreaded context
 */
@Singleton
public class SynchronizedThrottleAccumulator {

    private final InstantSource instantSource;
    private final ConcurrentLinkedQueue<ThrottleAccumulator> frontendThrottles = new ConcurrentLinkedQueue<>();

    @NonNull
    private Instant lastDecisionTime = Instant.EPOCH;

    @Inject
    public SynchronizedThrottleAccumulator(
            @NonNull final InstantSource instantSource,
            @NonNull @IngestThrottle final List<ThrottleAccumulator> frontendThrottles) {
        this.instantSource = requireNonNull(instantSource);
        this.frontendThrottles.addAll(requireNonNull(frontendThrottles, "frontendThrottles must not be null"));
    }

    /**
     * Updates the throttle requirements for the given transaction and returns whether the transaction
     * should be throttled for the current time(Instant.now).
     *
     * @param txnInfo the transaction to update the throttle requirements for
     * @param state the current state of the node
     * @return whether the transaction should be throttled
     */
    public boolean shouldThrottle(@NonNull TransactionInfo txnInfo, State state) {
        setDecisionTime(instantSource.instant());
        final var throttleFragment = frontendThrottles.poll();
        final var decision = requireNonNull(throttleFragment).shouldThrottle(txnInfo, lastDecisionTime, state);
        frontendThrottles.add(throttleFragment);
        return decision;
    }

    /**
     * Updates the throttle requirements for the given query and returns whether the query should be throttled for the
     * current time(Instant.now).
     *
     * @param queryFunction the functionality of the query
     * @param query the query to update the throttle requirements for
     * @param queryPayerId the payer id of the query
     * @return whether the query should be throttled
     */
    public boolean shouldThrottle(
            @NonNull final HederaFunctionality queryFunction,
            @NonNull final Query query,
            @Nullable AccountID queryPayerId) {
        requireNonNull(query);
        requireNonNull(queryFunction);
        setDecisionTime(instantSource.instant());

        final var throttleFragment = frontendThrottles.poll();
        final var decision =
                requireNonNull(throttleFragment).shouldThrottle(queryFunction, lastDecisionTime, query, queryPayerId);
        frontendThrottles.add(throttleFragment);
        return decision;
    }

    private void setDecisionTime(@NonNull final Instant time) {
        lastDecisionTime = time.isBefore(lastDecisionTime) ? lastDecisionTime : time;
    }
}

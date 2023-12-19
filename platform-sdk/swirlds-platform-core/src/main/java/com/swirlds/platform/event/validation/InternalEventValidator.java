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

package com.swirlds.platform.event.validation;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.consensus.GraphGenerations.FIRST_GENERATION;
import static com.swirlds.platform.system.events.EventConstants.GENERATION_UNDEFINED;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates that events are internally complete and consistent.
 */
public class InternalEventValidator {
    private static final Logger logger = LogManager.getLogger(InternalEventValidator.class);

    /**
     * The minimum period between log messages for a specific mode of failure.
     */
    private static final Duration MINIMUM_LOG_PERIOD = Duration.ofMinutes(1);

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    private final TransactionConfig transactionConfig;

    private final RateLimitedLogger nullHashedDataLogger;
    private final RateLimitedLogger nullUnhashedDataLogger;
    private final RateLimitedLogger tooManyTransactionBytesLogger;
    private final RateLimitedLogger inconsistentSelfParentLogger;
    private final RateLimitedLogger invalidGenerationLogger;

    private final LongAccumulator nullHashedDataAccumulator;
    private final LongAccumulator nullUnhashedDataAccumulator;
    private final LongAccumulator tooManyTransactionBytesAccumulator;
    private final LongAccumulator inconsistentSelfParentAccumulator;
    private final LongAccumulator invalidGenerationAccumulator;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param time               a time object, for rate limiting logging
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public InternalEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(time);

        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        this.transactionConfig = platformContext.getConfiguration().getConfigData(TransactionConfig.class);

        this.nullHashedDataLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.nullUnhashedDataLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.tooManyTransactionBytesLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.inconsistentSelfParentLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.invalidGenerationLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);

        this.nullHashedDataAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithNullHashedData")
                        .withDescription("Events that had null hashed data")
                        .withUnit("events"));
        this.nullUnhashedDataAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithNullUnhashedData")
                        .withDescription("Events that had null unhashed data")
                        .withUnit("events"));
        this.tooManyTransactionBytesAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithTooManyTransactionBytes")
                        .withDescription("Events that had more transaction bytes than permitted")
                        .withUnit("events"));
        this.inconsistentSelfParentAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithInconsistentSelfParent")
                        .withDescription("Events that had an internal self-parent inconsistency")
                        .withUnit("events"));
        this.invalidGenerationAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithInvalidGeneration")
                        .withDescription("Events with an invalid generation")
                        .withUnit("events"));
    }

    /**
     * Checks whether the required fields of an event are non-null.
     *
     * @param event the event to check
     * @return true if the required fields of the event are non-null, otherwise false
     */
    private boolean areRequiredFieldsNonNull(@NonNull final GossipEvent event) {
        if (event.getHashedData() == null) {
            // do not log the event itself, since toString would throw a NullPointerException
            nullHashedDataLogger.error(EXCEPTION.getMarker(), "Event has null hashed data");
            nullHashedDataAccumulator.update(1);
            return false;
        }

        if (event.getUnhashedData() == null) {
            // do not log the event itself, since toString would throw a NullPointerException
            nullUnhashedDataLogger.error(EXCEPTION.getMarker(), "Event has null unhashed data");
            nullUnhashedDataAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Checks whether the total byte count of all transactions in an event is less than the maximum.
     *
     * @param event the event to check
     * @return true if the total byte count of transactions in the event is less than the maximum, otherwise false
     */
    private boolean isTransactionByteCountValid(@NonNull final GossipEvent event) {
        int totalTransactionBytes = 0;
        for (final ConsensusTransaction transaction : event.getHashedData().getTransactions()) {
            totalTransactionBytes += transaction.getSerializedLength();
        }

        if (totalTransactionBytes > transactionConfig.maxTransactionBytesPerEvent()) {
            tooManyTransactionBytesLogger.error(
                    EXCEPTION.getMarker(),
                    "Event %s has %s transaction bytes, which is more than permitted"
                            .formatted(event, totalTransactionBytes));
            tooManyTransactionBytesAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Checks whether the parent hashes and generations of an event are internally consistent.
     *
     * @param event the event to check
     * @return true if the parent hashes and generations of the event are internally consistent, otherwise false
     */
    private boolean areParentsInternallyConsistent(@NonNull final GossipEvent event) {
        final BaseEventHashedData hashedData = event.getHashedData();

        // If a parent hash is missing, then the generation must also be invalid.
        // If a parent hash is not missing, then the generation must be valid.

        final Hash selfParentHash = hashedData.getSelfParentHash();
        final long selfParentGeneration = hashedData.getSelfParentGen();
        if ((selfParentHash == null) != (selfParentGeneration < FIRST_GENERATION)) {
            inconsistentSelfParentLogger.error(
                    EXCEPTION.getMarker(),
                    "Event %s has inconsistent self-parent hash and generation. Self-parent hash: %s, self-parent generation: %s"
                            .formatted(event, selfParentHash, selfParentGeneration));
            inconsistentSelfParentAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Checks whether the generation of an event is valid. A valid generation is one greater than the maximum generation
     * of the event's parents.
     *
     * @param event the event to check
     * @return true if the generation of the event is valid, otherwise false
     */
    private boolean isEventGenerationValid(@NonNull final GossipEvent event) {
        final long eventGeneration = event.getGeneration();

        long maxParentGeneration = GENERATION_UNDEFINED;
        for (final EventDescriptor parent : event) {
            maxParentGeneration = Math.max(maxParentGeneration, parent.getGeneration());
        }

        if (eventGeneration != maxParentGeneration + 1) {
            invalidGenerationLogger.error(
                    EXCEPTION.getMarker(),
                    "Event %s has an invalid generation. Event generation: %s, expected generation: %s"
                            .formatted(event, eventGeneration, maxParentGeneration + 1));
            invalidGenerationAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Validate the internal data integrity of an event.
     * <p>
     * If the event is determined to be valid, it is returned.
     *
     * @param event the event to validate
     * @return the event if it is valid, otherwise null
     */
    @Nullable
    public GossipEvent validateEvent(@NonNull final GossipEvent event) {
        if (areRequiredFieldsNonNull(event)
                && isTransactionByteCountValid(event)
                && areParentsInternallyConsistent(event)
                && isEventGenerationValid(event)) {
            return event;
        } else {
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());

            return null;
        }
    }
}

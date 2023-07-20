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

package com.swirlds.platform.test.event.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.GossipEventValidator;
import com.swirlds.platform.event.validation.GossipEventValidators;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.event.validation.TransactionSizeValidator;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.event.GossipEventBuilder;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class EventValidatorTests {
    private static final GossipEventValidator VALID = (e) -> true;
    private static final GossipEventValidator INVALID = (e) -> false;

    private static BaseEvent eventWithParents(
            final long selfParentGen,
            final long otherParentGen,
            final Hash selfParentHash,
            final Hash otherParentHash) {
        final BaseEvent event = mock(BaseEvent.class);
        final long creatorId = 0;
        final Instant timeCreated = Instant.now();
        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[0];
        Mockito.when(event.getHashedData())
                .thenReturn(new BaseEventHashedData(
                        new BasicSoftwareVersion(1),
                        new NodeId(creatorId),
                        selfParentGen,
                        otherParentGen,
                        selfParentHash,
                        otherParentHash,
                        timeCreated,
                        transactions));
        return event;
    }

    @Test
    void baseEventValidators() {
        final GossipEvent event = GossipEventBuilder.builder().buildEvent();
        assertTrue(new GossipEventValidators(List.of()).isEventValid(event));
        assertTrue(new GossipEventValidators(List.of(VALID)).isEventValid(event));
        assertTrue(new GossipEventValidators(List.of(VALID, VALID, VALID)).isEventValid(event));
        assertFalse(new GossipEventValidators(List.of(VALID, INVALID, VALID)).isEventValid(event));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1999, 2000, 2001, 10_000})
    void accumulatedTransactionSize(final int transAmount) {
        final int maxTransactionBytesPerEvent = 2000;
        final GossipEventValidator validator = new TransactionSizeValidator(maxTransactionBytesPerEvent);
        final GossipEvent event = GossipEventBuilder.builder()
                .setNumberOfTransactions(transAmount)
                .buildEvent();

        int eventTransSize = 0;
        for (final Transaction t : event.getHashedData().getTransactions()) {
            eventTransSize += t.getSerializedLength();
        }
        if (eventTransSize <= maxTransactionBytesPerEvent) {
            assertTrue(validator.isEventValid(event), "transaction limit should not have been exceeded");
        } else {
            assertFalse(validator.isEventValid(event), "transaction limit should have been exceeded");
        }
    }

    @Test
    void parentValidity() {
        final long undefinedGeneration = EventConstants.GENERATION_UNDEFINED;
        final long validGeneration = 10;
        final Hash hash1 = RandomUtils.randomHash();
        final Hash hash2 = RandomUtils.randomHash();
        final Hash nullHash = null;

        // has generation but no hash
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(validGeneration, undefinedGeneration, nullHash, nullHash)));
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, validGeneration, nullHash, nullHash)));

        // has hash but no generation
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, undefinedGeneration, hash1, nullHash)));
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, hash2)));

        // both parents same hash
        assertFalse(
                StaticValidators.isParentDataValid(eventWithParents(validGeneration, validGeneration, hash1, hash1)));

        // no parents
        assertTrue(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, nullHash)));

        // valid parents
        assertTrue(StaticValidators.isParentDataValid(
                eventWithParents(validGeneration, undefinedGeneration, hash1, nullHash)));
        assertTrue(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, validGeneration, nullHash, hash2)));
        assertTrue(
                StaticValidators.isParentDataValid(eventWithParents(validGeneration, validGeneration, hash1, hash2)));
    }

    @Test
    void invalidCreationTime() {
        final Instant time = Instant.now();
        final GossipEvent gossipEvent =
                GossipEventBuilder.builder().setTimestamp(time).buildEvent();
        final EventImpl event = new EventImpl(gossipEvent, null, null);

        event.setSelfParent(new EventImpl(
                GossipEventBuilder.builder().setTimestamp(time.plusNanos(100)).buildEvent(), null, null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");

        event.setSelfParent(new EventImpl(
                GossipEventBuilder.builder().setTimestamp(time.plusNanos(1)).buildEvent(), null, null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");

        event.setSelfParent(
                new EventImpl(GossipEventBuilder.builder().setTimestamp(time).buildEvent(), null, null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");
    }
}

/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.platform.state.service.PlatformStateFacade.DEFAULT_PLATFORM_STATE_FACADE;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.swirlds.common.Reservable;
import com.swirlds.platform.metrics.StateMetrics;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.merkle.MerkleStateRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StateEventHandlerManagerUtilsTests {

    @BeforeEach
    void setup() {}

    @Test
    void testFastCopyIsMutable() {

        final State state = new MerkleStateRoot();
        FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState(state);
        final Reservable reservable = state.cast();
        reservable.reserve();
        final StateMetrics stats = mock(StateMetrics.class);
        final State result = SwirldStateManagerUtils.fastCopy(
                state, stats, new BasicSoftwareVersion(1), DEFAULT_PLATFORM_STATE_FACADE);

        assertFalse(result.isImmutable(), "The copy state should be mutable.");
        assertEquals(
                1,
                reservable.getReservationCount(),
                "Fast copy should not change the reference count of the state it copies.");
        assertEquals(
                1,
                reservable.getReservationCount(),
                "Fast copy should return a new state with a reference count of 1.");
    }
}

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

package com.swirlds.platform.consensus;

import static org.mockito.Mockito.when;

import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.events.EventConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RoundCalculationUtilsTest {

    @Test
    void getOldestNonAncientRound() {
        Assertions.assertEquals(
                6,
                RoundCalculationUtils.getOldestNonAncientRound(5, 10),
                "if the latest 5 rounds are ancient, then the oldest non-ancient is 6");
        Assertions.assertEquals(
                EventConstants.MINIMUM_ROUND_CREATED,
                RoundCalculationUtils.getOldestNonAncientRound(10, 5),
                "if no rounds are ancient yet, then the oldest one is the first round");
    }

    @Test
    void getMinGenNonAncient() {
        // generation is equal to round*10
        final Map<Long, Long> map =
                LongStream.range(1, 50).collect(HashMap::new, (m, l) -> m.put(l, l * 10), HashMap::putAll);
        Assertions.assertEquals(
                60,
                RoundCalculationUtils.getMinGenNonAncient(5, 10, map::get),
                "if the oldest non-ancient round is 6, then the generation should 60");
        Assertions.assertEquals(
                10,
                RoundCalculationUtils.getMinGenNonAncient(10, 5, map::get),
                "if no rounds are ancient yet, then the minGenNonAncient is the first round generation");
        Assertions.assertEquals(
                GraphGenerations.FIRST_GENERATION,
                RoundCalculationUtils.getMinGenNonAncient(10, 5, l -> EventConstants.GENERATION_UNDEFINED),
                "if no round generation is not defined yet, then the minGenNonAncient is the first generation");
    }

    @Test
    void getMinGenNonAncientFromSignedState() {
        // generation is equal to round*10
        final Map<Long, Long> map =
                LongStream.range(1, 50).collect(HashMap::new, (m, l) -> m.put(l, l * 10), HashMap::putAll);
        final SignedState signedState = Mockito.mock(SignedState.class);
        final State state = Mockito.mock(State.class);
        final PlatformState platformState = Mockito.mock(PlatformState.class);
        final PlatformData platformData = Mockito.mock(PlatformData.class);
        when(signedState.getState()).thenReturn(state);
        when(state.getPlatformState()).thenReturn(platformState);
        when(platformState.getPlatformData()).thenReturn(platformData);

        final AtomicLong lastRoundDecided = new AtomicLong();
        when(signedState.getRound()).thenAnswer(a -> lastRoundDecided.get());
        when(signedState.getMinGen(Mockito.anyLong())).thenAnswer(a -> map.get(a.getArgument(0, Long.class)));
        when(platformData.getRound()).thenAnswer(a -> lastRoundDecided.get());
        when(platformData.getMinGen(Mockito.anyLong())).thenAnswer(a -> map.get(a.getArgument(0, Long.class)));

        lastRoundDecided.set(10);
        Assertions.assertEquals(
                60,
                RoundCalculationUtils.getMinGenNonAncient(5, signedState),
                "if the oldest non-ancient round is 6, then the generation should 60");
        lastRoundDecided.set(5);
        Assertions.assertEquals(
                10,
                RoundCalculationUtils.getMinGenNonAncient(10, signedState),
                "if no rounds are ancient yet, then the minGenNonAncient is the first round generation");
    }
}

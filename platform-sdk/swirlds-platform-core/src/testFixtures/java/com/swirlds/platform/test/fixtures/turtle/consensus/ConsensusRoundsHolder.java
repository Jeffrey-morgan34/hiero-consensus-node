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

package com.swirlds.platform.test.fixtures.turtle.consensus;

import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A test component collecting consensus rounds produced by the ConsensusEngine
 */
public interface ConsensusRoundsHolder {

    /**
     * Intercept the consensus rounds produced by the ConsensusEngine and adds them to a collection.
     *
     * @param rounds
     */
    void interceptRounds(final List<ConsensusRound> rounds);

    /**
     * Clear the internal state of this collector.
     *
     * @param ignored ignored trigger object
     */
    void clear(@NonNull final Object ignored);

    /**
     * Get the collected consensus rounds.
     *
     * @return the collected consensus rounds
     */
    List<ConsensusRound> getCollectedRounds();
}

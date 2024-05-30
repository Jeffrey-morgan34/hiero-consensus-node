/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.consensus;

import com.swirlds.platform.Consensus;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.wiring.component.InputWireLabel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Responsible for adding events to {@link Consensus}.
 */
public interface ConsensusEngine {

    /**
     * Update the platform status.
     *
     * @param platformStatus the new platform status
     */
    @InputWireLabel("PlatformStatus")
    void updatePlatformStatus(@NonNull PlatformStatus platformStatus);

    /**
     * Add an event to the hashgraph
     *
     * @param event an event to be added
     * @return a list of rounds that came to consensus as a result of adding the event
     */
    @NonNull
    @InputWireLabel("GossipEvent")
    List<ConsensusRound> addEvent(@NonNull GossipEvent event);

    /**
     * Perform an out-of-band snapshot update. This happens at restart/reconnect boundaries.
     *
     * @param snapshot the snapshot to adopt
     */
    void outOfBandSnapshotUpdate(@NonNull ConsensusSnapshot snapshot);

    /**
     * Extract a list of consensus events from a consensus round
     *
     * @return a list of consensus events
     */
    @NonNull
    default List<EventImpl> getConsensusEvents(@NonNull final ConsensusRound round) {
        return round.getConsensusEvents();
    }
}

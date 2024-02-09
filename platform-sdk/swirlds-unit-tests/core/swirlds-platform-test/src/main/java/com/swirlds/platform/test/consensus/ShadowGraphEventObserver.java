/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.consensus;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.platform.EventStrings;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowgraphInsertionException;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventAddedObserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Observes events and consensus in order to update the {@link Shadowgraph}
 */
public class ShadowGraphEventObserver implements EventAddedObserver, ConsensusRoundObserver {
    private static final Logger logger = LogManager.getLogger(ShadowGraphEventObserver.class);
    private final Shadowgraph shadowGraph;

    /**
     * Constructor.
     *
     * @param shadowGraph the {@link Shadowgraph} to update
     */
    public ShadowGraphEventObserver(@NonNull final Shadowgraph shadowGraph) {
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
    }

    /**
     * Expire events in {@link Shadowgraph} based on the new event window
     *
     * @param consensusRound a new consensus round
     */
    @Override
    public void consensusRound(final ConsensusRound consensusRound) {
        shadowGraph.updateEventWindow(consensusRound.getNonAncientEventWindow());
    }

    /**
     * Add an event to the {@link Shadowgraph}
     *
     * @param event the event to add
     */
    @Override
    public void eventAdded(final EventImpl event) {
        try {
            shadowGraph.addEvent(event);
        } catch (final ShadowgraphInsertionException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "failed to add event {} to shadow graph",
                    EventStrings.toMediumString(event),
                    e);
        }
    }
}

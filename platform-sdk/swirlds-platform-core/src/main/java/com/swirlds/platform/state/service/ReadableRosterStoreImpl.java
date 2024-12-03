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

package com.swirlds.platform.state.service;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Rosters.
 */
public class ReadableRosterStoreImpl implements ReadableRosterStore {

    /**
     * The roster state singleton. This is the state that holds the candidate roster hash and the list of pairs of round
     * and active roster hashes.
     */
    private final ReadableSingletonState<RosterState> rosterState;

    /**
     * The key-value map of roster hashes and rosters.
     */
    private final ReadableKVState<ProtoBytes, Roster> rosterMap;

    /**
     * Create a new {@link ReadableRosterStore} instance.
     *
     * @param readableStates The state to use.
     */
    public ReadableRosterStoreImpl(@NonNull final ReadableStates readableStates) {
        requireNonNull(readableStates);
        this.rosterState = readableStates.getSingleton(WritableRosterStore.ROSTER_STATES_KEY);
        this.rosterMap = readableStates.get(WritableRosterStore.ROSTER_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Roster getCandidateRoster() {
        final RosterState rosterStateSingleton = rosterState.get();
        if (rosterStateSingleton == null) {
            return null;
        }
        final Bytes candidateRosterHash = rosterStateSingleton.candidateRosterHash();
        return rosterMap.get(ProtoBytes.newBuilder().value(candidateRosterHash).build());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Roster getActiveRoster() {
        final var activeRosterHash = getCurrentRosterHash();
        if (activeRosterHash == null) {
            return null;
        }
        return rosterMap.get(ProtoBytes.newBuilder().value(activeRosterHash).build());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Roster get(@NonNull final Bytes rosterHash) {
        return rosterMap.get(ProtoBytes.newBuilder().value(rosterHash).build());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Bytes getCurrentRosterHash() {
        final RosterState rosterStateSingleton = rosterState.get();
        if (rosterStateSingleton == null) {
            return null;
        }
        final List<RoundRosterPair> rostersAndRounds = rosterStateSingleton.roundRosterPairs();
        if (rostersAndRounds.isEmpty()) {
            return null;
        }
        // by design, the first round roster pair is the active roster
        // this may need to be revisited when we reach DAB
        final RoundRosterPair latestRoundRosterPair = rostersAndRounds.getFirst();
        return latestRoundRosterPair.activeRosterHash();
    }

    @Nullable
    @Override
    public Bytes getPreviousRosterHash() {
        final var rosterHistory = getRosterHistory();
        return rosterHistory.size() > 1 ? rosterHistory.get(1).activeRosterHash() : null;
    }

    /** {@inheritDoc} */
    @Override
    public @NonNull List<RoundRosterPair> getRosterHistory() {
        return requireNonNull(rosterState.get()).roundRosterPairs().stream()
                .filter(pair -> rosterMap.contains(new ProtoBytes(pair.activeRosterHash())))
                .toList();
    }

    /**
     * @return the node ids from the roster entries of both active and candidate rosters combined
     */
    public List<EntityNumber> getCombinedRosterEntriesNodeIds() {
        final var activeRoster = getActiveRoster();
        final var candidateRoster = getCandidateRoster();
        List<EntityNumber> nodeEntriesInRosters = List.of();

        if (activeRoster != null && candidateRoster != null) {
            final Stream<Long> activeRosterEntries =
                    activeRoster.rosterEntries().stream().map(RosterEntry::nodeId);
            final Stream<Long> candidateRosterEntries =
                    candidateRoster.rosterEntries().stream().map(RosterEntry::nodeId);
            nodeEntriesInRosters = Stream.concat(activeRosterEntries, candidateRosterEntries)
                    .distinct()
                    .map(EntityNumber::new)
                    .toList();
        }
        // If we are here, at least one of the rosters is null
        // if activeRoster is not null, then candidateRoster is null
        // so we return only the entries from activeRoster.
        if (activeRoster != null) {
            nodeEntriesInRosters = activeRoster.rosterEntries().stream()
                    .map(RosterEntry::nodeId)
                    .map(EntityNumber::new)
                    .toList();
        }
        // If we are here, then activeRoster is null
        // so we check whether to return the candidateRoster's entries.
        if (candidateRoster != null) {
            nodeEntriesInRosters = candidateRoster.rosterEntries().stream()
                    .map(RosterEntry::nodeId)
                    .map(EntityNumber::new)
                    .toList();
        }
        return nodeEntriesInRosters;
    }
}

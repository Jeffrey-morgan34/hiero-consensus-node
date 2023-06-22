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

package com.swirlds.platform.event.tipset;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Stores a weighed tipset advancement score.
 * <p>
 * If zero stake nodes were not a thing, we could use a long to a tipset advancement score. Or, if it was ok to ignore
 * zero stake nodes, we could do the same as well. But since we don't want to allow zero sake nodes to get stale events,
 * we need to have a mechanism for separately tracking when zero stake nodes have advanced.
 *
 * @param advancementWeight          the advancement weight provided by nodes with non-zero stake. Contributes to
 *                                   meeting the threshold required to advance the current snapshot.
 * @param zeroStakeAdvancementWeight the advancement weight provided by nodes with zero stake, this is incremented by
 *                                   one for each zero stake node that advances. Does not contribute to meeting the
 *                                   threshold required to advance the current snapshot.
 */
public record TipsetAdvancementWeight(long advancementWeight, long zeroStakeAdvancementWeight) {

    /**
     * Zero advancement weight. For convenience.
     */
    public static final TipsetAdvancementWeight ZERO_ADVANCEMENT_WEIGHT = TipsetAdvancementWeight.of(0, 0);

    /**
     * Create a new instance of a tipset advancement score
     *
     * @param advancementWeight          the advancement weight provided by nodes with non-zero stake
     * @param zeroStakeAdvancementWeight the advancement weight provided by nodes with zero stake
     * @return a new instance of a tipset advancement score
     */
    public static TipsetAdvancementWeight of(final long advancementWeight, final long zeroStakeAdvancementWeight) {
        return new TipsetAdvancementWeight(advancementWeight, zeroStakeAdvancementWeight);
    }

    /**
     * Subtract two weights and return the result.
     *
     * @param that the weight to subtract from this weight
     * @return the result of subtracting the given weight from this weight
     */
    @NonNull
    public TipsetAdvancementWeight minus(@NonNull final TipsetAdvancementWeight that) {
        return new TipsetAdvancementWeight(
                advancementWeight - that.advancementWeight,
                zeroStakeAdvancementWeight - that.zeroStakeAdvancementWeight);
    }

    /**
     * Add two weights and return the result.
     *
     * @param that the weight to add to this weight
     * @return the result of adding the given weight to this weight
     */
    @NonNull
    public TipsetAdvancementWeight plus(@NonNull final TipsetAdvancementWeight that) {
        return new TipsetAdvancementWeight(
                advancementWeight + that.advancementWeight,
                zeroStakeAdvancementWeight + that.zeroStakeAdvancementWeight);
    }

    /**
     * Check if this weight is greater than the given weight. First {@link #advancementWeight()} is compared. If that is
     * equal, then {@link #zeroStakeAdvancementWeight()} breaks the tie.
     *
     * @param that the weight to compare to
     * @return true if this weight is greater than the given weight
     */
    public boolean isGreaterThan(@NonNull final TipsetAdvancementWeight that) {
        return advancementWeight > that.advancementWeight
                || (advancementWeight == that.advancementWeight
                        && zeroStakeAdvancementWeight > that.zeroStakeAdvancementWeight);
    }

    /**
     * Check if this weight is zero.
     */
    public boolean isNonzero() {
        return advancementWeight != 0 || zeroStakeAdvancementWeight != 0;
    }
}

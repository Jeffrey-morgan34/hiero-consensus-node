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

package com.hedera.node.app.service.mono.statedumpers.singleton;

import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record BBMCongestion(
        @Nullable List<ThrottleUsageSnapshot> tpsThrottles,
        @Nullable ThrottleUsageSnapshot gasThrottle,

        // last two represented as Strings already formatted from List<RichInstant>
        @Nullable String genericLevelStarts,
        @Nullable String gasLevelStarts) {
    static BBMCongestion fromMerkleNetworkContext(@NonNull final MerkleNetworkContext networkContext) {
        final var tpsThrottleUsageSnapshots = Arrays.stream(networkContext.usageSnapshots())
                .map(PbjConverter::toPbj)
                .toList();
        final var gasThrottleUsageSnapshot = PbjConverter.toPbj(networkContext.getGasThrottleUsageSnapshot());
        // format the following two from `List<RichInstant>` to String
        final var gasCongestionStarts = networkContext.getMultiplierSources() != null && networkContext.getMultiplierSources().gasCongestionStarts() != null
                ? Arrays.stream(networkContext.getMultiplierSources().gasCongestionStarts())
                        .map(RichInstant::fromJava)
                        .filter(Objects::nonNull)
                        .map(ThingsToStrings::toStringOfRichInstant)
                        .collect(Collectors.joining(", "))
                : "";
        final var genericCongestionStarts = networkContext.getMultiplierSources() != null && networkContext.getMultiplierSources().genericCongestionStarts() != null
                ? Arrays.stream(networkContext.getMultiplierSources().genericCongestionStarts())
                        .map(RichInstant::fromJava)
                        .filter(Objects::nonNull)
                        .map(ThingsToStrings::toStringOfRichInstant)
                        .collect(Collectors.joining(", "))
                : "";

        return new BBMCongestion(
                tpsThrottleUsageSnapshots, gasThrottleUsageSnapshot, genericCongestionStarts, gasCongestionStarts);
    }
}

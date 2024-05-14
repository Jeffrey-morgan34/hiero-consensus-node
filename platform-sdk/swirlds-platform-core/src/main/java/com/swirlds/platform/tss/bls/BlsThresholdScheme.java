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

package com.swirlds.platform.tss.bls;

import com.swirlds.platform.tss.Tss;
import com.swirlds.platform.tss.TssMessage;
import com.swirlds.platform.tss.TssPrivateKey;
import com.swirlds.platform.tss.TssPrivateShare;
import com.swirlds.platform.tss.TssPublicKey;
import com.swirlds.platform.tss.TssPublicShare;
import com.swirlds.platform.tss.TssShareClaim;
import com.swirlds.platform.tss.TssSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A BLS implementation of a Threshold Signature Scheme.
 */
public class BlsThresholdScheme implements Tss {
    @Nullable
    @Override
    public TssSignature aggregateSignatures(@NonNull final List<TssSignature> partialSignatures) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Nullable
    @Override
    public TssPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> partialShares) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Nullable
    @Override
    public TssPrivateKey aggregatePrivateKeys(@NonNull final List<TssPrivateKey> partialKeys) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(
            @NonNull final List<TssShareClaim> pendingShareClaims,
            @NonNull final TssPrivateShare privateShare,
            final int threshold) {
        throw new UnsupportedOperationException("Not implemented");
    }
}

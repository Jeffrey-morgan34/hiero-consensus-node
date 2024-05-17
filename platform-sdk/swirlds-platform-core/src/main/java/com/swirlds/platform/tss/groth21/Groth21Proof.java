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

package com.swirlds.platform.tss.groth21;

import com.swirlds.platform.tss.TssCiphertext;
import com.swirlds.platform.tss.TssCommitment;
import com.swirlds.platform.tss.TssProof;
import com.swirlds.platform.tss.pairings.Field;
import com.swirlds.platform.tss.pairings.FieldElement;
import com.swirlds.platform.tss.pairings.GroupElement;
import com.swirlds.platform.tss.signing.PublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A TSS proof, as utilized by the Groth21 scheme.
 * @param f TODO: which groups are these in?
 * @param a
 * @param y
 * @param z_r
 * @param z_a
 */
public record Groth21Proof<FE extends FieldElement<FE, F>, F extends Field<FE, F>, P extends PublicKey>(
        @NonNull GroupElement f,
        @NonNull GroupElement a,
        @NonNull GroupElement y,
        @NonNull FieldElement<FE, F> z_r,
        @NonNull FieldElement<FE, F> z_a)
        implements TssProof<P> {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verify(@NonNull final TssCiphertext<P> ciphertext, @NonNull final TssCommitment<P> commitment) {
        throw new UnsupportedOperationException("Not implemented");
    }
}

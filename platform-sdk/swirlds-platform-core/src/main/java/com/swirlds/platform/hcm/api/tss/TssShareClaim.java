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

package com.swirlds.platform.hcm.api.tss;

import com.swirlds.platform.hcm.api.signaturescheme.PairingPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A record that contains a share ID, and the ElGamal public key of the node that holds claim to the share.
 * <p>
 * The purpose of this record is to allow a node to produce a secret intended for a specific recipient, and to
 * encrypt that secret using the appropriate public key.
 *
 * @param shareId   the share ID
 * @param publicKey the public key
 */
public record TssShareClaim(@NonNull TssShareId shareId, @NonNull PairingPublicKey publicKey) {}

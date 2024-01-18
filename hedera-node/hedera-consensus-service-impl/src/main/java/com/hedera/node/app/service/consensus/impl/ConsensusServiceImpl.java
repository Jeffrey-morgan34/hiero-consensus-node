/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.schemas.InitialModServiceConsensusSchema;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Standard implementation of the {@link ConsensusService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ConsensusServiceImpl implements ConsensusService {
    public static final long RUNNING_HASH_VERSION = 3L;
    public static final int RUNNING_HASH_BYTE_ARRAY_SIZE = 48;
    public static final String TOPICS_KEY = "TOPICS";

    private MerkleMap<EntityNum, MerkleTopic> fs;

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry, final SemanticVersion version) {
        // We intentionally ignore the given (i.e. passed-in) version in this method
        registry.register(consensusSchema(version));
    }

    public void setFromState(MerkleMap<EntityNum, MerkleTopic> fs) {
        this.fs = fs;
    }

    private Schema consensusSchema(final SemanticVersion version) {
        final var consensusSchema = new InitialModServiceConsensusSchema(version, fs);

        // Once the 'from' state is passed in to the schema class, we don't need that reference in this class anymore.
        // We don't want to keep these references around because, in the case of migrating from mono to mod service, we
        // want the old mono state routes to disappear
        fs = null;
        return consensusSchema;
    }
}

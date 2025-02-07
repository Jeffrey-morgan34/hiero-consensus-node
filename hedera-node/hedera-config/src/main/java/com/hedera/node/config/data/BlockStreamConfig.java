/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.config.data;

import com.hedera.node.config.NetworkProperty;
import com.hedera.node.config.NodeProperty;
import com.hedera.node.config.types.BlockStreamWriterMode;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

/**
 * Configuration for the block stream.
 * @param streamMode Value of RECORDS disables the block stream; BOTH enables it
 * @param writerMode if we are writing to a file or gRPC stream
 * @param shutdownNodeOnNoBlockNodes whether to shutdown the consensus node if there are no block node connections
 * @param blockFileDir directory to store block files
 * @param blockNodeConnectionFileDir directory to get the block node configuration file
 * @param compressFilesOnCreation whether to compress files on creation
 * @param serializationBatchSize the number of items to serialize in a batch
 * @param hashCombineBatchSize the number of items to hash in a batch
 * @param roundsPerBlock the number of rounds per block
 * @param deleteFilesOnDisk whether to delete files on disk
 * @param grpcAddress the address of the gRPC server
 * @param grpcPort the port of the gRPC server
 */
@ConfigData("blockStream")
public record BlockStreamConfig(
        @ConfigProperty(defaultValue = "BOTH") @NetworkProperty StreamMode streamMode,
        @ConfigProperty(defaultValue = "FILE") @NodeProperty BlockStreamWriterMode writerMode,
        @ConfigProperty(defaultValue = "false") @NodeProperty boolean shutdownNodeOnNoBlockNodes,
        @ConfigProperty(defaultValue = "/opt/hgcapp/blockStreams") @NodeProperty String blockFileDir,
        @ConfigProperty(defaultValue = "/opt/hgcapp/data/config") @NodeProperty String blockNodeConnectionFileDir,
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean compressFilesOnCreation,
        @ConfigProperty(defaultValue = "32") @NetworkProperty int serializationBatchSize,
        @ConfigProperty(defaultValue = "32") @NetworkProperty int hashCombineBatchSize,
        @ConfigProperty(defaultValue = "1") @NetworkProperty int roundsPerBlock,
        @ConfigProperty(defaultValue = "false") @NodeProperty boolean deleteFilesOnDisk,
        @ConfigProperty(defaultValue = "localhost") String grpcAddress,
        @ConfigProperty(defaultValue = "8080") @Min(0) @Max(65535) int grpcPort) {

    /**
     * Check if the given account is a superuser.
     * @return true if the account is a superuser, false otherwise
     */
    public boolean streamToBlockNodes() {
        return writerMode != BlockStreamWriterMode.FILE;
    }
}

/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.validation;

/**
 * Various entity types in all services
 */
public enum EntityType {
    ACCOUNT,
    ALIAS,
    TOKEN,
    TOKEN_ASSOCIATION,
    NFT,
    AIRDROP,
    TOPIC,
    NODE,
    FILE,
    SCHEDULE,
    CONTRACT_BYTECODE,
    CONTRACT_STORAGE,
}

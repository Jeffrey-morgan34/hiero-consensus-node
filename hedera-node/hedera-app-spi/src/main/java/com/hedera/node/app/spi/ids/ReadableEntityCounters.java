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

package com.hedera.node.app.spi.ids;

import com.hedera.node.app.spi.validation.EntityType;

/**
 * Provides a way to get the entity type counter for a given entity type.
 */
public interface ReadableEntityCounters {
    /**
     * Returns the counter for the given entity type.
     * This is used to determine the size of the state.
     *
     * @param entityType the type of entity for which to get the counter
     * @return the counter for the given entity type
     */
    long getCounterFor(EntityType entityType);
}

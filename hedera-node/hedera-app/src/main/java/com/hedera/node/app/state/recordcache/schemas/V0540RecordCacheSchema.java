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

package com.hedera.node.app.state.recordcache.schemas;

import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TXN_RECORD_QUEUE;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.recordcache.TransactionReceiptEntries;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class V0540RecordCacheSchema extends Schema {
    public static final String TXN_RECEIPT_QUEUE = "TransactionReceiptQueue";
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(54).patch(0).build();

    public V0540RecordCacheSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.queue(TXN_RECEIPT_QUEUE, TransactionReceiptEntries.PROTOBUF));
    }

    @NonNull
    @Override
    public Set<String> statesToRemove() {
        return Set.of(TXN_RECORD_QUEUE);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        // Need to migrate records from TXN_RECORD_QUEUE to TXN_RECEIPT_QUEUE
    }
}

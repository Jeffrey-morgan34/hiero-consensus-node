/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.fixtures;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.NodeStakeUpdateRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jetbrains.annotations.NotNull;

public class FakeNodeStakeUpdateRecordBuilder {

    public NodeStakeUpdateRecordBuilder create() {
        return new NodeStakeUpdateRecordBuilder() {
            private String memo;
            private Transaction txn;
            private TransactionBody.DataOneOfType transactionBodyType;

            @NotNull
            @Override
            public NodeStakeUpdateRecordBuilder transaction(@NotNull final Transaction txn) {
                this.txn = txn;
                return this;
            }

            @NotNull
            @Override
            public NodeStakeUpdateRecordBuilder memo(@NotNull String memo) {
                this.memo = memo;
                return this;
            }

            @NotNull
            @Override
            public NodeStakeUpdateRecordBuilder transactionBodyType(
                    @NonNull final TransactionBody.DataOneOfType transactionBodyType) {
                this.transactionBodyType = transactionBodyType;
                return this;
            }
        };
    }
}

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

package com.hedera.node.app.blocks.impl;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;

import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * Base interface for objects that have extra context needed to easily translate a {@link TransactionResult} and,
 * optionally, a {@link TransactionOutput} into a {@link TransactionRecord} to be returned from a query.
 */
public interface TranslationContext {
    /**
     * Returns the memo of the transaction.
     * @return the memo
     */
    String memo();

    /**
     * The exchange rate set to include in the receipt for this transaction.
     * @return an exchange rate set applicable to the transaction receipt.
     */
    ExchangeRateSet transactionExchangeRates();

    /**
     * Returns the transaction ID of the transaction.
     * @return the transaction ID
     */
    TransactionID txnId();

    /**
     * Returns the serialized transaction.
     * @return the serialized transaction
     */
    Bytes serializedTransaction();

    /**
     * Returns the functionality of the transaction.
     * @return the functionality
     */
    HederaFunctionality functionality();

    /**
     * Returns the hash of the transaction.
     * @return the hash
     */
    default Bytes transactionHash() {
        return Bytes.wrap(noThrowSha384HashOf(serializedTransaction().toByteArray()));
    }
}

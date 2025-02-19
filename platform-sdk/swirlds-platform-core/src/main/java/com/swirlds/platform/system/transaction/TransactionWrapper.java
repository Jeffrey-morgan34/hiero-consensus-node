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

package com.swirlds.platform.system.transaction;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.util.TransactionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;

/**
 * A transaction that may or may not reach consensus.
 */
public non-sealed class TransactionWrapper implements ConsensusTransaction {

    /**
     * The consensus timestamp of this transaction, or null if consensus has not yet been reached.
     * NOT serialized and not part of object equality or hash code
     */
    private Instant consensusTimestamp;
    /** An optional metadata object set by the application */
    private Object metadata;
    /** The hash of the transaction */
    private Bytes hash;
    /** The bytes of the transaction */
    private final Bytes transaction;

    /**
     * Constructs a new transaction wrapper
     *
     * @param payloadBytes the serialized bytes of the transaction
     *
     * @throws NullPointerException if payloadBytes is null
     */
    public TransactionWrapper(@NonNull final Bytes payloadBytes) {
        this.transaction = payloadBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransactionWrapper that = (TransactionWrapper) o;
        return Objects.equals(getApplicationTransaction(), that.getApplicationTransaction());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getApplicationTransaction());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Sets the consensus timestamp of this transaction
     *
     * @param consensusTimestamp
     * 		the consensus timestamp
     */
    public void setConsensusTimestamp(@NonNull final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
    }

    @NonNull
    @Override
    public Bytes getApplicationTransaction() {
        return transaction;
    }

    /**
     * Get the serialized size of the transaction. This method returns the same value as
     * {@code SwirldsTransaction.getSerializedLength()} and {@code StateSignatureTransaction.getSerializedLength()}.
     *
     * @return the size of the transaction in the unit of byte
     */
    @Override
    public int getSize() {
        return TransactionUtils.getLegacyTransactionSize(transaction);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadata() {
        return (T) metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void setMetadata(@Nullable final T metadata) {
        this.metadata = metadata;
    }

    /**
     * Set the hash of the transaction
     * @param hash the hash of the transaction
     */
    public void setHash(@NonNull final Bytes hash) {
        this.hash = Objects.requireNonNull(hash, "hash should not be null");
    }

    /**
     * Get the hash of the transaction
     * @return the hash of the transaction
     * @throws NullPointerException may be thrown if the transaction is not yet hashed
     */
    @NonNull
    public Bytes getHash() {
        return Objects.requireNonNull(hash, "hash should not be null");
    }
}

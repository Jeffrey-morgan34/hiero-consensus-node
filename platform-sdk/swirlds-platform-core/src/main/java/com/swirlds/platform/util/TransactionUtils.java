// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.util;

import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class for handling PJB transactions.
 * <p>
 * <b>IMPORTANT:</b> This class is subject to deletion in the future. It's only needed for the transition period
 * from old serialization to PBJ serialization.
 */
public final class TransactionUtils {
    private TransactionUtils() {}

    /**
     * Get the size of a transaction.<br>
     * This is a convenience method that delegates to {@link #getLegacyTransactionSize(OneOf)}.
     *
     * @param transaction the transaction to get the size of
     * @return the size of the transaction
     */
    public static int getLegacyTransactionSize(@NonNull final EventTransaction transaction) {
        return getLegacyTransactionSize(transaction.transaction());
    }

    /**
     * Get the size of a transaction.
     *
     * @param transaction the transaction to get the size of
     * @return the size of the transaction
     */
    public static int getLegacyTransactionSize(@NonNull final OneOf<TransactionOneOfType> transaction) {
        if (TransactionOneOfType.APPLICATION_TRANSACTION.equals(transaction.kind())) {
            return Integer.BYTES // add the the size of array length field
                    + (int) ((Bytes) transaction.as()).length(); // add the size of the array
        } else if (TransactionOneOfType.STATE_SIGNATURE_TRANSACTION.equals(transaction.kind())) {
            final StateSignatureTransaction stateSignatureTransaction = transaction.as();
            return Long.BYTES // round
                    + Integer.BYTES // signature array length
                    + (int) stateSignatureTransaction.signature().length()
                    + Integer.BYTES // hash array length
                    + (int) stateSignatureTransaction.hash().length()
                    + Integer.BYTES; // epochHash, always null, which is SerializableStreamConstants.NULL_VERSION
        } else {
            throw new IllegalArgumentException("Unknown transaction type: " + transaction.kind());
        }
    }

    public static int getLegacyTransactionSize(@NonNull final Bytes transaction) {
        return Integer.BYTES // add the the size of array length field
                + (int) transaction.length(); // add the size of the array
    }

    /**
     * Check if a transaction is a system transaction.<br>
     * This is a convenience method that delegates to {@link #isSystemTransaction(OneOf)}.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a system transaction, {@code false} otherwise
     */
    public static boolean isSystemTransaction(final EventTransaction transaction) {
        return transaction == null ? false : isSystemTransaction(transaction.transaction());
    }

    /**
     * Check if a transaction is a system transaction.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a system transaction, {@code false} otherwise
     */
    public static boolean isSystemTransaction(@NonNull final OneOf<TransactionOneOfType> transaction) {
        return !TransactionOneOfType.APPLICATION_TRANSACTION.equals(transaction.kind());
    }
}

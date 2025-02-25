// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.history.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.History;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link HistoryLibraryCodec}.
 */
public enum HistoryLibraryCodecImpl implements HistoryLibraryCodec {
    HISTORY_LIBRARY_CODEC;

    @Override
    public @NonNull byte[] encodeHistory(@NonNull final History history) {
        requireNonNull(history);
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public @NonNull byte[] encodeLedgerId(
            @NonNull final byte[] addressBookHash, @NonNull final byte[] snarkVerificationKey) {
        requireNonNull(addressBookHash);
        requireNonNull(snarkVerificationKey);
        throw new UnsupportedOperationException("Not implemented");
    }
}

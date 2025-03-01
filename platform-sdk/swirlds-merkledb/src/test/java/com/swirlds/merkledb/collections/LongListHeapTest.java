// SPDX-License-Identifier: Apache-2.0
package com.swirlds.merkledb.collections;

import static com.swirlds.base.units.UnitConstants.MEBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class LongListHeapTest extends AbstractLongListTest<LongListHeap> {

    @Override
    protected LongListHeap createLongList() {
        return new LongListHeap();
    }

    @Override
    protected LongListHeap createLongListWithChunkSizeInMb(final int chunkSizeInMb) {
        final int impliedLongsPerChunk = Math.toIntExact((chunkSizeInMb * (long) MEBIBYTES_TO_BYTES) / Long.BYTES);
        return new LongListHeap(impliedLongsPerChunk);
    }

    @Override
    protected LongListHeap createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs) {
        return new LongListHeap(numLongsPerChunk, maxLongs, 0);
    }

    @Override
    protected LongListHeap createLongListFromFile(final Path file) throws IOException {
        return new LongListHeap(file, CONFIGURATION);
    }

    /**
     * Provides a stream of writer-reader pairs specifically for the {@link LongListHeap} implementation.
     * The writer is always {@link LongListHeap}, and it is paired with three reader implementations
     * (heap, off-heap, and disk-based). This allows for testing whether data written by the
     * {@link LongListHeap} can be correctly read back by all supported long list implementations.
     * <p>
     * This method builds on {@link AbstractLongListTest#longListWriterBasedPairsProvider} to generate
     * the specific writer-reader combinations for the {@link LongListHeap} implementation.
     *
     * @return a stream of argument pairs, each containing a {@link LongListHeap} writer
     *         and one of the supported reader implementations
     */
    static Stream<Arguments> longListWriterReaderPairsProvider() {
        return longListWriterBasedPairsProvider(heapWriterFactory);
    }

    /**
     * Provides a stream of writer paired with two reader implementations for testing
     * cross-compatibility.
     * <p>
     * Used for {@link AbstractLongListTest#testUpdateMinToTheLowerEnd}
     *
     * @return a stream of arguments containing a writer and two readers.
     */
    static Stream<Arguments> longListWriterSecondReaderPairsProvider() {
        return longListWriterSecondReaderPairsProviderBase(longListWriterReaderPairsProvider());
    }

    /**
     * Provides writer-reader pairs combined with range configurations for testing.
     * <p>
     * Used for {@link AbstractLongListTest#testWriteReadRangeElement}
     *
     * @return a stream of arguments for range-based parameterized tests
     */
    static Stream<Arguments> longListWriterReaderRangePairsProvider() {
        return longListWriterReaderRangePairsProviderBase(longListWriterReaderPairsProvider());
    }

    /**
     * Provides writer-reader pairs combined with chunk offset configurations (second set) for testing.
     * <p>
     * Used for {@link AbstractLongListTest#testPersistListWithNonZeroMinValidIndex}
     * and {@link AbstractLongListTest#testPersistShrunkList}
     *
     * @return a stream of arguments for chunk offset based parameterized tests
     */
    static Stream<Arguments> longListWriterReaderOffsetPairsProvider() {
        return longListWriterReaderOffsetPairsProviderBase(longListWriterReaderPairsProvider());
    }
}

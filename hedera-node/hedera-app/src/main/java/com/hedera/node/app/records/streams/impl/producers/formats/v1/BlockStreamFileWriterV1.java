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

package com.hedera.node.app.records.streams.impl.producers.formats.v1;

import static com.hedera.hapi.streams.v7.schema.BlockSchema.ITEMS;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.v7.BlockHashAlgorithm;
import com.hedera.hapi.streams.v7.BlockHeader;
import com.hedera.hapi.streams.v7.BlockSignatureAlgorithm;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamWriter;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BlockStreamFileWriterV1 implements BlockStreamWriter {

    private static final Logger logger = LogManager.getLogger(BlockStreamFileWriterV1.class);

    /** The file extension for block files. */
    public static final String RECORD_EXTENSION = "blk";

    /** The suffix added to RECORD_EXTENSION when they are compressed. */
    public static final String COMPRESSION_ALGORITHM_EXTENSION = ".gz";

    /** Whether to compress the record file and sidecar files. */
    private final boolean compressFiles;

    /** The node-specific path to the directory where block files are written */
    private final Path nodeScopedBlockDir;

    /** The path to the record file we are writing */
    private Path blockFilePath;

    /** The file output stream we are writing to, which writes to {@link #blockFilePath} */
    private OutputStream outputStream;

    private WritableStreamingData writableStreamingData;

    /** The state of this writer */
    private State state;

    private enum State {
        UNINITIALIZED,
        OPEN_WITHOUT_HEADER,
        OPEN,
        CLOSED
    }

    public BlockStreamFileWriterV1(
            @NonNull final BlockStreamConfig config,
            @NonNull final NodeInfo nodeInfo,
            @NonNull final FileSystem fileSystem) {

        if (config.blockVersion() != 7) {
            logger.fatal(
                    "Bad configuration: BlockStreamFileWriterV1 used with block version {}", config.blockVersion());
            throw new IllegalArgumentException("Configuration block version must be >= 7 for BlockStreamFileWriterV1!");
        }

        this.state = State.UNINITIALIZED;
        this.compressFiles = config.compressFilesOnCreation();

        // Compute directories for record and sidecar files.
        final Path blockDir = fileSystem.getPath(config.logDir());
        nodeScopedBlockDir = blockDir.resolve("block" + nodeInfo.memo());

        // Create parent directories if needed for the record file itself.
        try {
            Files.createDirectories(nodeScopedBlockDir);
        } catch (final IOException e) {
            logger.fatal("Could not create block directory {}", nodeScopedBlockDir, e);
            throw new UncheckedIOException(e);
        }
    }

    // =================================================================================================================
    // Implementation of methods in BlockStreamWriter

    @Override
    public void init(final long blockNumber) {
        logger.info("Initializing block stream writer {}", blockNumber);
        if (state != State.UNINITIALIZED)
            throw new IllegalStateException("Cannot initialize a BlockStreamFileWriterV1 twice");

        if (blockNumber < 0) throw new IllegalArgumentException("Block number must be non-negative");

        // Create the chain of streams.
        this.blockFilePath = getBlockFilePath(blockNumber);

        OutputStream out = null;
        try {
            out = Files.newOutputStream(blockFilePath);
            out = new BufferedOutputStream(out, 1024 * 1024); // 1 MB
            if (compressFiles) {
                out = new GZIPOutputStream(out, 1024 * 256); // 256 KB
                // This double buffer is needed to reduce the number of synchronized calls to the underlying
                // GZIPOutputStream. We know most files are going to be ~3-4 MB, so we can safely buffer that much.
                out = new BufferedOutputStream(out, 1024 * 1024 * 4); // 4 MB
            }

            this.writableStreamingData = new WritableStreamingData(out);
        } catch (final IOException e) {
            // If an exception was thrown, we should close the stream if it was opened to prevent a resource leak.
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    logger.error("Error closing the BlockStreamFileWriterV1 output stream", ex);
                }
            }
            // We must be able to produce blocks.
            logger.fatal("Could not create block file {}", blockFilePath, e);
            throw new UncheckedIOException(e);
        }
        this.outputStream = out;

        state = State.OPEN;
    }

    /**
     * writeItem writes each BlockItem to the output stream.
     * @param item the serialized BlockItem to write
     */
    @Override
    public void writeItem(@NonNull final Bytes item) {
        System.out.println(
                "Writing bytes to block stream - Bytes hash: " + item.hashCode() + " - File: " + this.blockFilePath);
        assert item.length() > 0 : "BlockItem must be non-empty";
        if (state != State.OPEN) {
            throw new IllegalStateException("Cannot write to a BlockStreamFileWriterV1 that is not open");
        }

        // Write the ITEMS tag.
        ProtoWriterTools.writeTag(writableStreamingData, ITEMS, ProtoConstants.WIRE_TYPE_DELIMITED);
        // Write the length of the item.
        writableStreamingData.writeVarInt((int) item.length(), false);
        // Write the item bytes themselves.
        item.writeTo(writableStreamingData);
        System.out.println(
                "Wrote bytes to block stream - Bytes hash: " + item.hashCode() + " - File: " + this.blockFilePath);
    }

    @Override
    public void close() {
        if (state.ordinal() < State.OPEN.ordinal()) {
            throw new IllegalStateException("Cannot close a BlockStreamFileWriterV1 that is not open");
        }

        // Flush and then close the outputStream.
        try {
            // FUTURE: We can remove the reference to the outputStream once this is added to PBJ and call flush directly
            // on the writableStreamingData. https://github.com/hashgraph/pbj/issues/183
            outputStream.flush();
            writableStreamingData.close();
            state = State.CLOSED;
        } catch (final IOException e) {
            logger.error("Error closing the BlockStreamFileWriterV1 output stream", e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Get the record file path for a record file with the given consensus time
     *
     * @param blockNumber the block number for the block file
     * @return Path to a record file for that block number
     */
    @NonNull
    private Path getBlockFilePath(final long blockNumber) {
        return nodeScopedBlockDir.resolve(longToFileName(blockNumber) + "." + RECORD_EXTENSION
                + (compressFiles ? COMPRESSION_ALGORITHM_EXTENSION : ""));
    }

    @NonNull
    public static String longToFileName(final long value) {
        // Convert the signed long to an unsigned long using BigInteger for correct representation
        BigInteger unsignedValue =
                BigInteger.valueOf(value & Long.MAX_VALUE).add(BigInteger.valueOf(Long.MIN_VALUE & value));

        // Format the unsignedValue as a 20-character string, padded with leading zeros to ensure we have enough digits
        // for an unsigned long. However, to allow for future expansion, we use 36 characters as that's what UUID uses.
        return String.format("%036d", unsignedValue);
    }
}

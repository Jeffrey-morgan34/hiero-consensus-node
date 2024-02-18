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

package com.hedera.node.app.records.streams.impl.producers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.HashAlgorithm;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.v7.BlockHashAlgorithm;
import com.hedera.hapi.streams.v7.BlockHeader;
import com.hedera.hapi.streams.v7.BlockSignatureAlgorithm;
import com.hedera.hapi.streams.v7.StateChanges;
import com.hedera.node.app.records.streams.ProcessUserTransactionResult;
import com.hedera.node.app.records.streams.impl.BlockStreamProducer;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A single threaded implementation of {@link BlockStreamProducer} where operations may block the single calling thread.
 * This simple implementation is also useful for testing and for the "Hedera Local Node", which is a version of a
 * consensus node that may be run as a standalone application, useful for development. This class is not thread-safe. It
 * is wrapped by {@link ConcurrentBlockStreamProducer} to make it thread-safe and asynchronous, so it does not block the
 * "handle" thread.
 *
 * <p>A BlockStreamProducer is only responsible for delegating serialization of data it's passed from write methods and
 *    computing running hashes from the serialized data.
 */
public final class BlockStreamProducerSingleThreaded implements BlockStreamProducer {
    /** The logger */
    private static final Logger logger = LogManager.getLogger(BlockStreamProducerSingleThreaded.class);
    /** Creates new {@link BlockStreamWriter} instances */
    private final BlockStreamWriterFactory writerFactory;
    /** The {@link BlockStreamFormat} used to serialize items for output. */
    private final BlockStreamFormat format;
    /**
     * The {@link BlockStreamWriter} to use for writing produced blocks. A new one is created at the beginning
     * of each new block.
     */
    private BlockStreamWriter writer;
    /** The running hash at end of the last user transaction */
    private Bytes runningHash = null;
    /** The previous running hash */
    private Bytes runningHashNMinus1 = null;
    /** The previous, previous running hash */
    private Bytes runningHashNMinus2 = null;
    /** The previous, previous, previous running hash */
    private Bytes runningHashNMinus3 = null;

    private long currentBlockNumber = -1;

    //    private SoftwareVersion lastConsensusEventVersion;

    /**
     * Construct BlockStreamProducerSingleThreaded
     *
     * @param nodeInfo the current node information
     * @param format The format to use for the block stream
     * @param writerFactory constructs the writers for the block stream, one per block
     */
    @Inject
    public BlockStreamProducerSingleThreaded(
            @NonNull final SelfNodeInfo nodeInfo,
            @NonNull final BlockStreamFormat format,
            @NonNull final BlockStreamWriterFactory writerFactory) {
        this.writerFactory = requireNonNull(writerFactory);
        this.format = requireNonNull(format);
    }

    // =========================================================================================================================================================================
    // public methods

    /** {@inheritDoc} */
    @Override
    public void initFromLastBlock(@NonNull final RunningHashes runningHashes, final long lastBlockNumber) {
        if (this.runningHash != null) {
            throw new IllegalStateException("initFromLastBlock() must only be called once");
        }

        this.runningHash = runningHashes.runningHash();
        this.runningHashNMinus1 = runningHashes.nMinus1RunningHash();
        this.runningHashNMinus2 = runningHashes.nMinus2RunningHash();
        this.runningHashNMinus3 = runningHashes.nMinus3RunningHash();
        this.currentBlockNumber = lastBlockNumber;
    }

    /** {@inheritDoc} */
    public void beginBlock() {
        this.currentBlockNumber++;

        final var lastRunningHash = getRunningHashObject();

        logger.debug(
                "Initializing block stream writer for block {} with running hash {}",
                currentBlockNumber,
                lastRunningHash);

        openWriter(this.currentBlockNumber, lastRunningHash);

        // Write the block header to the block stream as the first item in the block.
        writeBlockHeader(lastRunningHash);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public CompletableFuture<BlockEnder> endBlock(@NonNull final BlockEnder.Builder builder) {
        return CompletableFuture.completedFuture(builder.setLastRunningHash(getRunningHashObject())
                .setWriter(writer)
                .setFormat(format)
                .setBlockNumber(currentBlockNumber)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        // FUTURE: close() should wait until the block is completed, which cannot happen until the block proof is
        // produced, and that cannot happen until the signatures have been gossiped. Today, this will close in
        // unpredictable ways, and will most likely result in the block being written incompletely without a block
        // proof.
        final var lastRunningHash = getRunningHashObject();
        closeWriter(lastRunningHash, this.currentBlockNumber);

        runningHash = null;
        runningHashNMinus1 = null;
        runningHashNMinus2 = null;
        runningHashNMinus3 = null;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Bytes getRunningHash() {
        assert runningHash != null : "initFromLastBlock() must be called before getRunningHash()";
        return runningHash;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public Bytes getNMinus3RunningHash() {
        return runningHashNMinus3;
    }

    /** {@inheritDoc} */
    public void writeConsensusEvent(@NonNull final ConsensusEvent consensusEvent) {
        System.out.println("Block: " + this.currentBlockNumber + " - Serializing event: " + consensusEvent.hashCode());
        final var serializedBlockItem = format.serializeConsensusEvent(consensusEvent);
        updateRunningHashes(serializedBlockItem);
        System.out.println("Block: " + this.currentBlockNumber + " - Serialized event: " + consensusEvent.hashCode()
                + " - Bytes hash: " + serializedBlockItem.hashCode());
        writeSerializedBlockItem(serializedBlockItem);
    }

    /** {@inheritDoc} */
    public void writeSystemTransaction(@NonNull final ConsensusTransaction systemTxn) {
        final var serializedBlockItem = format.serializeSystemTransaction(systemTxn);
        updateRunningHashes(serializedBlockItem);
        System.out.println("Block: " + this.currentBlockNumber + " - Serialized system transaction: "
                + systemTxn.hashCode() + " - Bytes hash: " + serializedBlockItem.hashCode());
        writeSerializedBlockItem(serializedBlockItem);
    }

    /** {@inheritDoc} */
    public void writeUserTransactionItems(@NonNull final ProcessUserTransactionResult result) {
        // We reuse this messageDigest to avoid creating a new one for each item.
        final MessageDigest messageDigest = format.getMessageDigest();
        result.transactionRecordStream().forEach(item -> {
            final var serializedBlockItems = format.serializeUserTransaction(item);
            serializedBlockItems.forEach(serializedBlockItem -> {
                updateRunningHashesWithMessageDigest(messageDigest, serializedBlockItem);
                System.out.println("Block: " + this.currentBlockNumber + " - Serialized user transaction: "
                        + serializedBlockItem.hashCode() + " - Bytes hash: " + serializedBlockItem.hashCode());
                writeSerializedBlockItem(serializedBlockItem);
            });
        });
    }

    /** {@inheritDoc} */
    public void writeStateChanges(@NonNull final StateChanges stateChanges) {
        final var serializedBlockItem = format.serializeStateChanges(stateChanges);
        updateRunningHashes(serializedBlockItem);
        System.out.println("Block: " + this.currentBlockNumber + " - Serialized state changes: "
                + serializedBlockItem.hashCode() + " - Bytes hash: " + serializedBlockItem.hashCode());
        writeSerializedBlockItem(serializedBlockItem);
    }

    private void writeBlockHeader(@NonNull final HashObject lastRunningHash) {
        final SemanticVersion blockVersion = new SemanticVersion(7, 0, 0, null, null);
        final SemanticVersion hapiProtoVersion = new SemanticVersion(7, 0, 0, null, null);
        final SemanticVersion servicesVersion = new SemanticVersion(7, 0, 0, null, null);
        final SemanticVersion platformVersion = new SemanticVersion(7, 0, 0, null, null);
        final long number = this.currentBlockNumber;
        final Bytes startRunningHash = lastRunningHash.hash();
        final BlockHashAlgorithm hashAlgorithm = BlockHashAlgorithm.BLOCKHASHALGORITHM_SHA_384;
        final BlockSignatureAlgorithm signatureAlgorithm =
                BlockSignatureAlgorithm.BLOCKSIGNATUREALGORITHM_SHA_384_WITH_RSA;
        final var blockHeader = new BlockHeader(
                blockVersion,
                hapiProtoVersion,
                servicesVersion,
                platformVersion,
                number,
                startRunningHash,
                hashAlgorithm,
                signatureAlgorithm);
        final var serializedBlockItem = format.serializeBlockHeader(blockHeader);
        updateRunningHashes(serializedBlockItem);
        System.out.println("Block: " + this.currentBlockNumber + " - Serialized block header: "
                + serializedBlockItem.hashCode() + " - Bytes hash: " + serializedBlockItem.hashCode());
        writeSerializedBlockItem(serializedBlockItem);
    }

    // =================================================================================================================
    // private implementation

    // TODO(nickpoorman): The software version can change throughout an event.
    //    private boolean isFirstConsensusEventInBlock() {
    //        return this.lastConsensusEventVersion == null;
    //    }

    //    private boolean hasConsensusEventVersionChanged(@NonNull final ConsensusEvent newConsensusEvent) {
    //        final var oldVersion = requireNonNull(this.lastConsensusEventVersion, "lastConsensusEventVersion is
    // null");
    //        // Check if the current consensus event version is different from newConsensusEvent.
    //        var newVersion = newConsensusEvent.getSoftwareVersion();
    //        return newVersion.compareTo(oldVersion) != 0;
    //    }

    @NonNull
    private HashObject asHashObject(@NonNull final Bytes hash) {
        return new HashObject(HashAlgorithm.SHA_384, (int) hash.length(), hash);
    }

    @NonNull
    private HashObject getRunningHashObject() {
        return asHashObject(getRunningHash());
    }

    private void closeWriter(@NonNull final HashObject lastRunningHash, final long lastBlockNumber) {
        if (writer != null) {
            logger.debug(
                    "Closing block record writer for block {} with running hash {}", lastBlockNumber, lastRunningHash);

            // If we fail to close the writer, then this node is almost certainly going to end up in deep trouble.
            // Make sure this is logged. In the FUTURE we may need to do something more drastic, like shut down the
            // node, or maybe retry a number of times before giving up.
            try {
                writer.close();
            } catch (final Exception e) {
                logger.error("Error closing block record writer for block {}", lastBlockNumber, e);
            }
        }
    }

    /**
     * openWriter uses the writerFactory to create a new writer and initialize it for each block. The writer is not
     * re-used.
     * @param newBlockNumber
     * @param lastRunningHash
     */
    private void openWriter(final long newBlockNumber, @NonNull final HashObject lastRunningHash) {
        try {
            // Depending on the configuration, this writer's methods may be asynchronous or synchronous. The
            // BlockStreamWriterFactory instantiated by dagger will determine this.
            writer = writerFactory.create();
            writer.init(currentBlockNumber);
        } catch (final Exception e) {
            // This represents an almost certainly fatal error. In the FUTURE we should look at dealing with this in a
            // more comprehensive and consistent way. Maybe we retry a bunch of times before giving up, then restart
            // the node. Or maybe we block forever. Or maybe we disable event intake while we keep trying to get this
            // to work. Or maybe we just shut down the node.
            logger.error("Error creating or initializing a block record writer for block {}", newBlockNumber, e);
            throw e;
        }
    }

    private void updateRunningHashes(@NonNull final Bytes serializedItem) {
        updateRunningHashesWithMessageDigest(format.getMessageDigest(), serializedItem);
    }

    private void updateRunningHashesWithMessageDigest(
            @NonNull final MessageDigest messageDigest, @NonNull final Bytes serializedItem) {
        // Compute new running hash, by adding each serialized BlockItem to the current running hash.
        runningHashNMinus3 = runningHashNMinus2;
        runningHashNMinus2 = runningHashNMinus1;
        runningHashNMinus1 = runningHash;
        runningHash = format.computeNewRunningHash(messageDigest, runningHash, serializedItem);
    }

    private void writeSerializedBlockItem(@NonNull final Bytes serializedItem) {
        try {
            // Depending on the configuration, this writeItem may be an asynchronous or synchronous operation. The
            // BlockStreamWriterFactory instantiated by dagger will determine this.
            writer.writeItem(serializedItem);
        } catch (final Exception e) {
            // This **may** prove fatal. The node should be able to carry on, but then fail when it comes to
            // actually producing a valid record stream file. We need to have some way of letting all nodesknow
            // that this node has a problem, so we can make sure at least a minimal threshold of nodes is
            // successfully producing a blockchain.
            logger.error("Error writing block item to block stream writer for block {}", currentBlockNumber, e);
        }
    }
}

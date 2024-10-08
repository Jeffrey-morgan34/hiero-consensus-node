package com.hedera.node.app.blocks;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.block.stream.schema.BlockItemSchema;
import com.hedera.hapi.streams.HashObject;
import com.hedera.node.app.blocks.impl.ConcurrentStreamingTreeHasher;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static com.hedera.hapi.streams.schema.RecordStreamFileSchema.END_OBJECT_RUNNING_HASH;
import static com.hedera.node.app.blocks.impl.NaiveStreamingTreeHasher.hashNaively;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeMessage;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class SerializationBenchmark {
    public static void main(String... args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{"com.hedera.node.app.blocks.SerializationBenchmark.serializeItem"});
    }

    public enum BlockType {
        TRANSACTION, TRANSACTION_RESULT, TRANSACTION_OUTPUT, KV_STATE_CHANGES, SINGLETON_STATE_CHANGES
    }

    private static final String SAMPLE_BLOCK = "sample.blk.gz";
    private static final Map<BlockType, BlockItem> SAMPLE_ITEMS = new HashMap<>();
    private static final Map<BlockType, Integer> SAMPLE_ITEM_SIZES = new HashMap<>();

    //    @Param({"TRANSACTION_RESULT"})
    @Param({"SINGLETON_STATE_CHANGES"})
    private BlockType blockType;

    @Param({"1000"})
    private int numItems;

    private Bytes expectedAnswer;
    private ByteBuffer buffer;

    @Setup(Level.Trial)
    public void setup() throws IOException, ParseException {
        try (final var fin = SerializationBenchmark.class.getClassLoader().getResourceAsStream(SAMPLE_BLOCK)) {
            try (final GZIPInputStream in = new GZIPInputStream(fin)) {
                final var block = Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
                for (final var item : block.items()) {
                    switch (item.item().kind()) {
                        case EVENT_TRANSACTION -> SAMPLE_ITEMS.put(BlockType.TRANSACTION, item);
                        case TRANSACTION_RESULT -> SAMPLE_ITEMS.put(BlockType.TRANSACTION_RESULT, item);
                        case TRANSACTION_OUTPUT -> SAMPLE_ITEMS.put(BlockType.TRANSACTION_OUTPUT, item);
                        case STATE_CHANGES -> {
                            final var stateChanges = item.stateChangesOrThrow().stateChanges().getFirst();
                            if (stateChanges.hasMapUpdate()) {
                                SAMPLE_ITEMS.put(BlockType.KV_STATE_CHANGES, item);
                            } else if (stateChanges.hasSingletonUpdate()) {
                                SAMPLE_ITEMS.put(BlockType.SINGLETON_STATE_CHANGES, item);
                            }
                        }
                    }
                }
                SAMPLE_ITEMS.forEach((type, item) -> SAMPLE_ITEM_SIZES.put(type, (int) BlockItem.PROTOBUF.toBytes(item).length()));
            }
        }
        final var item = SAMPLE_ITEMS.get(blockType);
        final var serializedItem = BlockItem.PROTOBUF.toBytes(item).toByteArray();
        final var itemSize = (int) BlockItem.PROTOBUF.toBytes(item).length();
        final var bytes = new byte[numItems * SAMPLE_ITEM_SIZES.get(blockType)];
        for (int i = 0; i < numItems; i++) {
            System.arraycopy(serializedItem, 0, bytes, i * itemSize, itemSize);
        }
        expectedAnswer = Bytes.wrap(bytes);
        buffer = ByteBuffer.allocate(bytes.length);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void serializeItem(@NonNull final Blackhole blackhole) throws IOException {
        final var item = SAMPLE_ITEMS.get(blockType);

//        final var serializedItems = new ArrayList<Bytes>(numItems);
//        for (int i = 0; i < numItems; i++) {
//            serializedItems.add(BlockItem.PROTOBUF.toBytes(item));
//        }
//        blackhole.consume(serializedItems);

        final var outputStream = BufferedData.wrap(buffer);
        for (int i = 0; i < numItems; i++) {
            writeMessage(
                    outputStream,
                    BlockItemSchema.STATE_CHANGES,
                    item.stateChangesOrThrow(),
                    StateChanges.PROTOBUF::write,
                    StateChanges.PROTOBUF::measureRecord);
        }
        final var bytes = buffer.array();
        if (buffer.position() != (int) expectedAnswer.length()) {
            throw new IllegalStateException();
        }
        buffer.rewind();
        blackhole.consume(bytes);
    }
}

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

package com.swirlds.state.merkle.disk;

import static com.swirlds.state.merkle.logging.StateLogger.logQueueIterate;
import static com.swirlds.state.merkle.logging.StateLogger.logQueuePeek;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.merkle.queue.QueueNode;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableQueueStateBase;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

// TODO: double check if OnDiskValue<E> should be used as previously
/**
 * An implementation of {@link ReadableQueueState} that uses a merkle {@link QueueNode} as the backing store.
 * @param <E> The type of elements in the queue.
 */
public class OnDiskReadableQueueState<E> extends ReadableQueueStateBase<E> {

    @NonNull
    private final VirtualMap megaMap;

    @NonNull
    private final Codec<E> valueCodec;

    // Queue head index. This is the index at which the next element is retrieved from
    // a queue. If equal to tail, the queue is empty
    private long head = 1;

    // Queue tail index. This is the index at which the next element will be added to
    // a queue. Queue size therefore is tail - head
    private long tail = 1;

    /** Create a new instance */
    public OnDiskReadableQueueState(
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            @NonNull final Codec<E> valueCodec,
            @NonNull final VirtualMap megaMap) {
        super(serviceName, stateKey);

        this.valueCodec = requireNonNull(valueCodec);
        this.megaMap = Objects.requireNonNull(megaMap);
    }

    @Nullable
    @Override
    protected E peekOnDataSource() {
        final var value = isEmpty() ? null : getFromStore(head);
        // Log to transaction state log, what was peeked
        logQueuePeek(getLabel(), value);
        return value;
    }

    @NonNull
    private E getFromStore(final long index) {
        final var value = megaMap.get(getMegaMapKey(index), valueCodec);
        if (value == null) {
            throw new IllegalStateException("Can't find queue element at index " + index + " in the store");
        }
        return value;
    }

    @NonNull
    @Override
    protected Iterator<E> iterateOnDataSource() {
        final QueueIterator it = new QueueIterator(head, tail);
        // Log to transaction state log, what was iterated
        logQueueIterate(getLabel(), tail - head, it);
        it.reset();
        return it;
    }

    // TODO: test this method
    // TODO: refactor? (it is duplicated in OnDiskWritableQueueState)
    /**
     * Generates a 10-byte big-endian key identifying an element in the Mega Map.
     * <ul>
     *   <li>The first 2 bytes store the unsigned 16-bit state ID</li>
     *   <li>The next 8 bytes store the {@code index}</li>
     * </ul>
     *
     * @param index the element index within this queue
     * @return a {@link Bytes} object containing exactly 10 bytes in big-endian order
     * @throws IllegalArgumentException if the state ID is outside [0..65535]
     */
    private Bytes getMegaMapKey(final long index) {
        final int stateId = getStateId();

        if (stateId < 0 || stateId > 65535) {
            throw new IllegalArgumentException("State ID " + stateId + " must fit in [0..65535]");
        }

        final ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) stateId);
        buffer.putLong(index);

        return Bytes.wrap(buffer.array());
    }

    /**
     * Returns if this queue node state is empty, i.e. if the head and tail indexes are equal.
     */
    public boolean isEmpty() {
        return head == tail;
    }

    /**
     * A tiny utility class to iterate over the queue node.
     */
    private class QueueIterator implements Iterator<E> {

        // Queue position to start from, inclusive
        private final long start;

        // Queue position to iterate up to, exclusive
        private final long limit;

        // The current iterator position, start <= current < limit
        private long current;

        // Start (inc), limit (exc)
        public QueueIterator(final long start, final long limit) {
            this.start = start;
            this.limit = limit;
            reset();
        }

        @Override
        public boolean hasNext() {
            return current < limit;
        }

        @Override
        public E next() {
            if (current == limit) {
                throw new NoSuchElementException();
            }
            try {
                return getFromStore(current++);
            } catch (final IllegalStateException e) {
                throw new ConcurrentModificationException(e);
            }
        }

        void reset() {
            current = start;
        }
    }
}

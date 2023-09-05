/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import static com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey.BYTES_IN_SERIALIZED_FORM;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.SequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class EntityNumVirtualKeySerializerTest {
    private final long longKey = 2;
    private final long otherLongKey = 3;

    private final EntityNumVirtualKeySerializer subject = new EntityNumVirtualKeySerializer();

    @Test
    void gettersWork() {
        final var bin = mock(ByteBuffer.class);

        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.getSerializedSize());
        assertEquals(EntityNumVirtualKeySerializer.DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(EntityNumVirtualKeySerializer.CLASS_ID, subject.getClassId());
        assertEquals(EntityNumVirtualKeySerializer.CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void deserializeUsingPbjWorks() {
        final var in = mock(ReadableSequentialData.class);
        final var expectedKey = new EntityNumVirtualKey(longKey);
        given(in.readLong()).willReturn(longKey);

        assertEquals(expectedKey, subject.deserialize(in));
    }

    @Test
    void deserializeUsingByteBufferWorks() throws IOException {
        final var bin = mock(ByteBuffer.class);
        final var expectedKey = new EntityNumVirtualKey(longKey);
        given(bin.getLong()).willReturn(longKey);

        assertEquals(expectedKey, subject.deserialize(bin, 1));
    }

    @Test
    void serializeUsingPbjWorks() throws IOException {
        final var out = BufferedData.allocate(32);
        final var virtualKey = new EntityNumVirtualKey(longKey);

        final long origPos = out.position();
        subject.serialize(virtualKey, out);
        final long finalPos = out.position();
        assertEquals(BYTES_IN_SERIALIZED_FORM, finalPos - origPos);

        assertEquals(longKey, out.getLong(0));
    }

    @Test
    void serializeUsingByteBufferWorks() throws IOException {
        final var out = ByteBuffer.allocate(8);
        final var virtualKey = new EntityNumVirtualKey(longKey);

        subject.serialize(virtualKey, out);
        assertEquals(BYTES_IN_SERIALIZED_FORM, out.position());
        assertEquals(longKey, out.getLong(0));
    }

    @Test
    void equalsUsingBufferedDataWorks() throws IOException {
        final var someKey = new EntityNumVirtualKey(longKey);
        final var diffNum = new EntityNumVirtualKey(otherLongKey);

        final var buf = mock(BufferedData.class);
        given(buf.readLong()).willReturn(someKey.getKeyAsLong());

        assertTrue(subject.equals(buf, someKey));
        assertFalse(subject.equals(buf, diffNum));
    }

    @Test
    void equalsUsingByteBufferWorks() throws IOException {
        final var someKey = new EntityNumVirtualKey(longKey);
        final var diffNum = new EntityNumVirtualKey(otherLongKey);

        final var bin = mock(ByteBuffer.class);
        given(bin.getLong()).willReturn(someKey.getKeyAsLong());

        assertTrue(subject.equals(bin, 1, someKey));
        assertFalse(subject.equals(bin, 1, diffNum));
    }

    @Test
    void serdesAreNoop() {
        assertDoesNotThrow(() -> subject.deserialize((SerializableDataInputStream) null, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}

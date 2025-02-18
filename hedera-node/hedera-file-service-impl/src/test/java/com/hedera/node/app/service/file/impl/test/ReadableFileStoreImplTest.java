/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.hapi.utils.EntityType;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadableFileStoreImplTest extends FileTestBase {
    private ReadableFileStoreImpl subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableFileStoreImpl(readableStates, readableEntityCounters);
    }

    @Test
    void getsFileMetadataIfFileExists() {
        givenValidFile();
        final var fileMeta = subject.getFileMetadata(WELL_KNOWN_FILE_ID);

        assertNotNull(fileMeta);

        assertEquals(fileId, fileMeta.fileId());
        assertEquals(keys, fileMeta.keys());

        assertEquals(memo, fileMeta.memo());
        assertFalse(fileMeta.deleted());
        assertEquals(Bytes.wrap(contents), fileMeta.contents());
    }

    @Test
    void missingFileIsNull() {
        readableFileState.reset();
        final var state =
                MapReadableKVState.<Long, File>builder(FileService.NAME, FILES).build();
        given(readableStates.<Long, File>get(FILES)).willReturn(state);
        subject = new ReadableFileStoreImpl(readableStates, readableEntityCounters);

        assertThat(subject.getFileMetadata(WELL_KNOWN_FILE_ID)).isNull();
    }

    @Test
    void constructorCreatesFileState() {
        final var store = new ReadableFileStoreImpl(readableStates, readableEntityCounters);
        assertNotNull(store);
    }

    @Test
    void nullArgsFail() {
        assertThrows(NullPointerException.class, () -> new ReadableFileStoreImpl(null, readableEntityCounters));
    }

    @Test
    void returnSizeOfState() {
        final var store = new ReadableFileStoreImpl(readableStates, readableEntityCounters);
        assertEquals(readableEntityCounters.getCounterFor(EntityType.FILE), store.sizeOfState());
    }
}

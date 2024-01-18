/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.InitialModFileGenesisSchema;
import com.hedera.node.app.service.mono.files.DataMapFactory;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.files.store.FcBlobsBytesStore;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.config.ConfigProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Standard implementation of the {@link FileService} {@link com.hedera.node.app.spi.Service}. */
public final class FileServiceImpl implements FileService {
    public static final String BLOBS_KEY = "FILES";
    public static final String UPGRADE_FILE_KEY = "UPGRADE_FILE";
    public static final String UPGRADE_DATA_KEY = "UPGRADE_DATA[%s]";
    private ConfigProvider configProvider;

    @Inject
    public FileServiceImpl(ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    private Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss;
    private Map<com.hederahashgraph.api.proto.java.FileID, byte[]> fileContents;
    private Map<com.hederahashgraph.api.proto.java.FileID, HFileMeta> fileAttrs;

    public void setFs(Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss) {
        this.fss = fss;
        var blobStore = new FcBlobsBytesStore(fss);
        this.fileContents = DataMapFactory.dataMapFrom(blobStore);
        this.fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, final SemanticVersion version) {
        registry.register(new InitialModFileGenesisSchema(version, configProvider, fss, fileContents, fileAttrs));

        // Once the 'from' state is passed in to the schema class, we don't need that reference in this class anymore.
        // We don't want to keep these references around because, in the case of migrating from mono to mod service, we
        // want the old mono state routes to disappear
        fss = null;
        fileContents = null;
        fileAttrs = null;
    }
}

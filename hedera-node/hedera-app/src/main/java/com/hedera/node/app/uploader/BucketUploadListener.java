/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.cloud.uploader;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

/**
 * Interface for components that need to be notified when block files are closed and ready for upload.
 */
public interface BucketUploadListener {
    /**
     * Called when a block file is closed and ready for upload.
     *
     * @param blockPath The path to the closed block file
     */
    void onBlockClosed(@NonNull Path blockPath);
}

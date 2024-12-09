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

package com.hedera.node.config.data;

import static java.util.Objects.requireNonNull;

import com.hedera.node.config.types.BucketProvider;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for the cloud bucket uploader.
 * @param name       the name
 * @param provider   the provider to use for the upload
 * @param endpoint   the endpoint
 * @param region     the region (required only for AWS)
 * @param bucketName the name of the bucket
 */
public record CloudBucketConfig(
        @ConfigProperty String name,
        @ConfigProperty BucketProvider provider,
        @ConfigProperty String endpoint,
        @ConfigProperty String region, // required for AWS only
        @ConfigProperty String bucketName) {
    public CloudBucketConfig {
        requireNonNull(name, "name cannot be null");
        requireNonNull(provider, "provider cannot be null");
        requireNonNull(endpoint, "endpoint cannot be null");
        requireNonNull(bucketName, "bucketName cannot be null");

        if (provider.equals(BucketProvider.AWS)) {
            if (requireNonNull(region).isBlank()) {
                throw new IllegalArgumentException("region cannot be null if the provider is AWS");
            }
        }
    }
}

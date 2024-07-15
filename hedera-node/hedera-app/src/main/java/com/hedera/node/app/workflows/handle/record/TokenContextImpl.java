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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.node.app.records.RecordBuildersImpl.castBuilder;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.stack.DispatchSavepoint;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;

public class TokenContextImpl implements TokenContext, FinalizeContext {
    private final Configuration configuration;
    private final ReadableStoreFactory readableStoreFactory;
    private final WritableStoreFactory writableStoreFactory;
    private final BlockRecordManager blockRecordManager;
    private final Instant consensusTime;
    private final SavepointStackImpl stack;
    private final DispatchSavepoint savepoint;
    private final SingleTransactionRecordBuilder baseBuilder;

    public TokenContextImpl(
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final SavepointStackImpl stack,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final Instant consensusTime,
            @NonNull final DispatchSavepoint savepoint,
            @NonNull final SingleTransactionRecordBuilder baseBuilder) {
        this.stack = requireNonNull(stack);
        this.savepoint = requireNonNull(savepoint);
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        this.blockRecordManager = requireNonNull(blockRecordManager, "blockRecordManager must not be null");
        this.baseBuilder = requireNonNull(baseBuilder);
        if (stack.peek() != savepoint.current()) {
            throw new IllegalArgumentException("Stack must be at the savepoint");
        }

        this.readableStoreFactory = new ReadableStoreFactory(stack);
        this.writableStoreFactory =
                new WritableStoreFactory(stack, TokenService.NAME, configuration, storeMetricsService);
        this.consensusTime = requireNonNull(consensusTime, "consensusTime must not be null");
    }

    @NonNull
    @Override
    public Instant consensusTime() {
        return consensusTime;
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return configuration;
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return readableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T writableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return writableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T userTransactionRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castBuilder(baseBuilder, recordBuilderClass);
    }

    @Override
    public boolean hasChildOrPrecedingRecords() {
        return savepoint.current().hasBuilderOtherThan(baseBuilder);
    }

    @Override
    public <T> void forEachChildRecord(@NonNull Class<T> recordBuilderClass, @NonNull Consumer<T> consumer) {
        requireNonNull(consumer, "consumer must not be null");
        savepoint.current().forEachOtherBuilder(consumer, recordBuilderClass, baseBuilder);
    }

    @NonNull
    @Override
    public <T> T addPrecedingChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = stack.createIrreversiblePrecedingBuilder();
        return castBuilder(result, recordBuilderClass);
    }

    @Override
    public boolean isScheduleDispatch() {
        return savepoint.category() == SCHEDULED;
    }

    @Override
    public void markMigrationRecordsStreamed() {
        blockRecordManager.markMigrationRecordsStreamed();
    }

    @Override
    public Set<Long> knownNodeIds() {
        return readableStoreFactory.getStore(ReadableStakingInfoStore.class).getAll();
    }
}

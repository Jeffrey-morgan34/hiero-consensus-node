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

package com.hedera.node.app.workflows.handle.stack;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.IRREVERSIBLE;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.ReadonlyStatesWrapper;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.OutputItemStream;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.savepoints.BuilderSinkImpl;
import com.hedera.node.app.workflows.handle.stack.savepoints.FirstChildSavepoint;
import com.hedera.node.app.workflows.handle.stack.savepoints.FirstRootSavepoint;
import com.hedera.node.app.workflows.handle.stack.savepoints.FollowingSavepoint;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A stack of savepoints scoped to a dispatch. Each savepoint captures the state of the {@link HederaState} at the time
 * the savepoint was created and all the changes made to the state from the time savepoint was created, along with all
 * the stream builders created in the savepoint.
 */
public class SavepointStackImpl implements HandleContext.SavepointStack, HederaState {
    private final HederaState root;
    private final Deque<Savepoint> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();
    /**
     * The stream builder for the transaction whose dispatch created this stack.
     */
    private final SingleTransactionRecordBuilder baseBuilder;
    // For the root stack of a user dispatch, the final sink of all created stream builders; otherwise null,
    // because child stacks flush their builders into the savepoint at the top of their parent stack
    @Nullable
    private final BuilderSink builderSink;
    /**
     * If creating blocks, the K/V and queue state changes committed by the most recent call to commitFullStack().
     */
    @Nullable
    private List<BlockItem> stateChanges;

    /**
     * Constructs the root {@link SavepointStackImpl} for the given state at the start of handling a user transaction.
     *
     * @param root the root state
     * @param maxBuildersBeforeUser the maximum number of preceding builders with available consensus times
     * @param maxBuildersAfterUser the maximum number of following builders with available consensus times
     * @return the root {@link SavepointStackImpl}
     */
    public static SavepointStackImpl newRootStack(
            @NonNull final HederaState root, final int maxBuildersBeforeUser, final int maxBuildersAfterUser) {
        return new SavepointStackImpl(root, maxBuildersBeforeUser, maxBuildersAfterUser);
    }

    /**
     * Constructs a new child {@link SavepointStackImpl} for the given state, where the child dispatch has the given
     * reversing behavior, transaction category, and record customizer.
     *
     * @param root the state on which the child dispatch is based
     * @param reversingBehavior the reversing behavior for the initial dispatch
     * @param category the transaction category
     * @param customizer the record customizer
     * @return the child {@link SavepointStackImpl}
     */
    public static SavepointStackImpl newChildStack(
            @NonNull final SavepointStackImpl root,
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        return new SavepointStackImpl(root, reversingBehavior, category, customizer);
    }

    /**
     * Constructs a new root {@link SavepointStackImpl} with the given root state.
     *
     * @param root the root state
     * @param maxBuildersBeforeUser the maximum number of preceding builders to create
     * @param maxBuildersAfterUser the maximum number of following builders to create
     */
    private SavepointStackImpl(
            @NonNull final HederaState root, final int maxBuildersBeforeUser, final int maxBuildersAfterUser) {
        this.root = requireNonNull(root);
        builderSink = new BuilderSinkImpl(maxBuildersBeforeUser, maxBuildersAfterUser + 1);
        setupFirstSavepoint(USER);
        baseBuilder = peek().createBuilder(REVERSIBLE, USER, NOOP_RECORD_CUSTOMIZER, true);
    }

    /**
     * Constructs a new child {@link SavepointStackImpl} with the given parent stack and the provided
     * characteristics of the dispatch.
     * @param parent the parent stack
     * @param reversingBehavior the reversing behavior of the dispatch
     * @param category the category of the dispatch
     * @param customizer the record customizer for the dispatch
     */
    private SavepointStackImpl(
            @NonNull final SavepointStackImpl parent,
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        requireNonNull(reversingBehavior);
        requireNonNull(customizer);
        requireNonNull(category);
        this.root = requireNonNull(parent);
        this.builderSink = null;
        setupFirstSavepoint(category);
        baseBuilder = peek().createBuilder(reversingBehavior, category, customizer, true);
    }

    @Override
    public void createSavepoint() {
        stack.push(new FollowingSavepoint(new WrappedHederaState(peek().state()), peek()));
    }

    @Override
    public void commit() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("The savepoint stack is empty");
        }
        stack.pop().commit();
    }

    @Override
    public void rollback() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("The savepoint stack is empty");
        }
        stack.pop().rollback();
    }

    @Override
    public int depth() {
        return stack.size();
    }

    /**
     * Commits all state changes captured in this stack.
     */
    public void commitFullStack() {
        // TODO - traverse the queue and K/V WritableStates in the first savepoint WrappedHederaState's
        // writableStatesMap and create a StateChanges list
        while (!stack.isEmpty()) {
            stack.pop().commit();
        }
        setupFirstSavepoint(baseBuilder.category());
    }

    /**
     * Rolls back all state changes captured in this stack.
     */
    public void rollbackFullStack() {
        while (!stack.isEmpty()) {
            stack.pop().rollback();
        }
        setupFirstSavepoint(baseBuilder.category());
    }

    /**
     * Returns the root {@link ReadableStates} for the given service name.
     *
     * @param serviceName the name of the service
     * @return the root {@link ReadableStates} for the given service name
     */
    @NonNull
    public ReadableStates rootStates(@NonNull final String serviceName) {
        return root.getReadableStates(serviceName);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@link ReadableStates} instances returned from this method are based on the {@link WritableStates} instances
     * for the same service name. This means that any modifications to the {@link WritableStates} will be reflected
     * in the {@link ReadableStates} instances returned from this method.
     * <p>
     * Unlike other {@link HederaState} implementations, the returned {@link ReadableStates} of this implementation
     * must only be used in the handle workflow.
     */
    @Override
    @NonNull
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return new ReadonlyStatesWrapper(getWritableStates(serviceName));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method guarantees that the same {@link WritableStates} instance is returned for the same {@code serviceName}
     * to ensure all modifications to a {@link WritableStates} are kept together.
     */
    @Override
    @NonNull
    public WritableStates getWritableStates(@NonNull final String serviceName) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return writableStatesMap.computeIfAbsent(serviceName, s -> new WritableStatesStack(this, s));
    }

    @NonNull
    @Override
    public <T extends SingleTransactionRecordBuilder> T getBaseBuilder(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castBuilder(baseBuilder, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = createReversibleChildBuilder();
        return castBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = createRemovableChildBuilder();
        return castBuilder(result, recordBuilderClass);
    }

    public static <T> T castBuilder(
            @NonNull final SingleTransactionRecordBuilder builder, @NonNull final Class<T> builderClass) {
        if (!builderClass.isInstance(builder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return builderClass.cast(builder);
    }

    /**
     * May only be called on the root stack to get the entire list of stream builders created in the course
     * of handling a user transaction.
     * @return all stream builders created when handling the user transaction
     * @throws NullPointerException if called on a non-root stack
     */
    public List<SingleTransactionRecordBuilder> allStreamBuilders() {
        return requireNonNull(builderSink).allBuilders();
    }

    /**
     * May only be called on the root stack to determine if this stack has capacity to create more system records to
     * as preceding dispatches.
     * @return whether there are more system records to be created
     * @throws NullPointerException if called on a non-root stack
     */
    public boolean hasMoreSystemRecords() {
        return requireNonNull(builderSink).precedingCapacity() > 0;
    }

    /**
     * Whether this stack has accumulated any stream builders other than its base builder; important to know when
     * determining the record finalization work to be done.
     * @return whether this stack has any stream builders other than the base builder
     */
    public boolean hasNonBaseStreamBuilder() {
        if (builderSink != null && builderSink.hasBuilderOtherThan(baseBuilder)) {
            return true;
        }
        for (final var savepoint : stack) {
            if (savepoint.hasBuilderOtherThan(baseBuilder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For each stream builder in this stack other than the designated base builder, invokes the given consumer
     * with the builder cast to the given type.
     * @param builderClass the type to cast the builders to
     * @param consumer the consumer to invoke
     * @param <T> the type to cast the builders to
     */
    public <T> void forEachNonBaseBuilder(@NonNull final Class<T> builderClass, @NonNull final Consumer<T> consumer) {
        requireNonNull(builderClass);
        requireNonNull(consumer);
        if (builderSink != null) {
            builderSink.forEachOtherBuilder(consumer, builderClass, baseBuilder);
        }
        for (var savepoint : stack) {
            savepoint.forEachOtherBuilder(consumer, builderClass, baseBuilder);
        }
    }

    /**
     * Returns the {@link HandleContext.TransactionCategory} of the transaction that created this stack.
     * @return the transaction category
     */
    public HandleContext.TransactionCategory txnCategory() {
        return baseBuilder.category();
    }

    /**
     * Creates a new stream builder for a removable child in the active savepoint.
     * @return the new stream builder
     */
    public SingleTransactionRecordBuilder createRemovableChildBuilder() {
        return peek().createBuilder(REMOVABLE, CHILD, NOOP_RECORD_CUSTOMIZER, false);
    }

    /**
     * Creates a new stream builder for a reversible child in the active savepoint.
     * @return the new stream builder
     */
    public SingleTransactionRecordBuilder createReversibleChildBuilder() {
        return peek().createBuilder(REVERSIBLE, CHILD, NOOP_RECORD_CUSTOMIZER, false);
    }

    /**
     * Creates a new stream builder for an irreversible preceding transaction in the active savepoint.
     * @return the new stream builder
     */
    public SingleTransactionRecordBuilder createIrreversiblePrecedingBuilder() {
        return peek().createBuilder(IRREVERSIBLE, PRECEDING, NOOP_RECORD_CUSTOMIZER, false);
    }

    /**
     * Returns all following child records in the stack for use in end-of-EVM-transaction throttling.
     * <p>
     * To be removed on completion of HIP-993 and adoption of per-dispatch throttling.
     *
     * @return the list of child records
     */
    @Deprecated
    public List<SingleTransactionRecordBuilder> getChildBuilders() {
        final var childRecords = new ArrayList<SingleTransactionRecordBuilder>();
        for (final var savepoint : stack) {
            for (final var builder : savepoint.followingBuilders()) {
                if (builder.category() == CHILD) {
                    childRecords.add(builder);
                }
            }
        }
        return childRecords;
    }

    /**
     * Returns the top savepoint without removing it from the stack. Used only by the {@link WritableStatesStack},
     * not part of the public API.
     *
     * @return the top savepoint
     * @throws IllegalStateException if the stack has been committed already
     */
    @NonNull
    Savepoint peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return stack.peek();
    }

    /**
     * Builds all the records for the user transaction.
     *
     * @param consensusTime consensus time of the transaction
     * @return the stream of records
     */
    public OutputItemStream buildStreamItems(@NonNull final Instant consensusTime) {
        final List<SingleTransactionRecord> records = new ArrayList<>();
        final var builders = allStreamBuilders();
        TransactionID.Builder idBuilder = null;
        int indexOfUserRecord = 0;
        for (int i = 0; i < builders.size(); i++) {
            if (builders.get(i).category() == USER) {
                indexOfUserRecord = i;
                idBuilder = builders.get(i).transactionID().copyBuilder();
                break;
            }
        }
        int nextNonce = 1;
        for (int i = 0; i < builders.size(); i++) {
            final var builder = builders.get(i);
            final var nonce =
                    switch (builder.category()) {
                        case USER, SCHEDULED -> 0;
                        case PRECEDING, CHILD -> nextNonce++;
                    };
            // The schedule service specifies the transaction id to use for a triggered transaction
            if (builder.transactionID() == null || TransactionID.DEFAULT.equals(builder.transactionID())) {
                builder.transactionID(requireNonNull(idBuilder).nonce(nonce).build())
                        .syncBodyIdFromRecordId();
            }
            final var consensusNow = consensusTime.plusNanos((long) i - indexOfUserRecord);
            builder.consensusTimestamp(consensusNow);
            if (i > indexOfUserRecord && builder.category() != SCHEDULED) {
                builder.parentConsensus(consensusTime);
            }
            records.add(((SingleTransactionRecordBuilderImpl) builder).build());
        }
        // TODO: Add block items
        return new OutputItemStream(List.of(), records);
    }

    private void setupFirstSavepoint(@NonNull final HandleContext.TransactionCategory category) {
        if (root instanceof SavepointStackImpl parent) {
            stack.push(new FirstChildSavepoint(new WrappedHederaState(root), parent.peek(), category));
        } else {
            stack.push(new FirstRootSavepoint(new WrappedHederaState(root), requireNonNull(builderSink)));
        }
    }
}

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

package com.swirlds.state.spi;

import com.hedera.pbj.runtime.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;

/**
 * A readable queue of elements.
 *
 * @param <E> The type of element held in the queue.
 */
public interface ReadableQueueState<E> {
    /**
     * Gets the "state key" that uniquely identifies this {@link ReadableQueueState} within the {@link
     * Schema} which are scoped to the service implementation. The key is therefore not globally
     * unique, only unique within the service implementation itself.
     *
     * <p>The call is idempotent, always returning the same value. It must never return null.
     *
     * @return The state key. This will never be null, and will always be the same value for an
     *     instance of {@link ReadableQueueState}.
     */
    @NonNull
    String getStateKey(); // TODO: remove in favor of `getStateId` ?

    /**
     * Gets the "state id" that uniquely identifies this {@link ReadableKVState} within the
     * {@link com.swirlds.state.lifecycle.Service} and {@link Schema}. It is globally unique.
     *
     * <p>The call is idempotent, always returning the same value. It must never return null.
     *
     * @return The state id. This will always be the same value for an
     *     instance of {@link ReadableKVState}.
     */
    int getStateId();

    /**
     * Retrieves but does not remove the element at the head of the queue, or returns null if the queue is empty.
     *
     * @return The element at the head of the queue, or null if the queue is empty.
     */
    @Nullable
    E peek();

    /**
     * An iterator over all elements in the queue without removing any elements from the queue.
     * @return An iterator over all elements in the queue.
     */
    @NonNull
    Iterator<E> iterator();
}

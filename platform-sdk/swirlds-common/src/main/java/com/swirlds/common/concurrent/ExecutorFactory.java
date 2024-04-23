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

package com.swirlds.common.concurrent;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A factory for creating executors and threads.
 */
public interface ExecutorFactory {

    /**
     * Create a ForkJoinPool with the given parallelism.
     *
     * @param parallelism the parallelism
     * @return the ForkJoinPool
     */
    @NonNull
    ForkJoinPool createForkJoinPool(int parallelism);

    /**
     * Create an ExecutorService with the given thread count.
     *
     * @param threadCount the thread count
     * @return the ExecutorService
     */
    @NonNull
    ExecutorService createExecutorService(int threadCount);

    /**
     * Create a ScheduledExecutorService with the given thread count.
     *
     * @param threadCount the thread count
     * @return the ScheduledExecutorService
     */
    ScheduledExecutorService createScheduledExecutorService(int threadCount);

    /**
     * Create a not started thread with the given runnable.
     *
     * @param runnable the runnable
     * @return the not started thread
     * @deprecated all useage should be migrated to {@link #createExecutorService(int)}
     */
    @Deprecated
    @NonNull
    Thread createThread(@NonNull Runnable runnable);
}

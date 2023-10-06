/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring;

import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Wires two components together.
 *
 * @param <T> the type of object that is passed through the wire
 */
public interface Wire<T> {

    /**
     * Get a new wire builder.
     *
     * @param name     the name of the wire. Used for metrics and debugging. Must be unique (not enforced by framework).
     *                 Must only contain alphanumeric characters, underscores, and hyphens (enforced by framework).
     * @param consumer tasks are passed to this consumer
     * @param <T>      the type of object that is passed through the wire
     * @return a new wire builder
     */
    static <T> WireBuilder<T> builder(@NonNull final String name, @NonNull final Consumer<T> consumer) {
        return new WireBuilder<>(name, consumer);
    }

    /**
     * Get a new wire metrics builder. Can be passed to {@link WireBuilder#withMetricsBuilder(WireMetricsBuilder)} to
     * add metrics to the wire.
     *
     * @param metrics the metrics framework
     * @return a new wire metrics builder
     */
    static WireMetricsBuilder metricsBuilder(@NonNull final Metrics metrics) {
        return new WireMetricsBuilder(metrics);
    }

    /**
     * Get the name of the wire.
     *
     * @return the name of the wire
     */
    @NonNull
    String getName();

    /**
     * Add a task to the wire. May block if back pressure is enabled. Similar to {@link #interruptablePut(Object)}
     * except that it cannot be interrupted and can block forever if backpressure is enabled.
     *
     * @param data the data to be processed by the wire
     */
    void put(@NonNull T data);

    /**
     * Add a task to the wire. May block if back pressure is enabled. If backpressure is enabled and being applied, this
     * method can be interrupted.
     *
     * @param data the data to be processed by the wire
     * @throws InterruptedException if the thread is interrupted while waiting for capacity to become available
     */
    void interruptablePut(@NonNull T data) throws InterruptedException;

    /**
     * Add a task to the wire. If backpressure is enabled and there is not immediately capacity available, this method
     * will not accept the data.
     *
     * @param data the data to be processed by the wire
     * @return true if the data was accepted, false otherwise
     */
    boolean offer(@NonNull T data);

    /**
     * Get the number of unprocessed tasks. Returns -1 if this wire is not monitoring the number of unprocessed tasks.
     * Wires do not track the number of unprocessed tasks by default. To enable tracking, enable
     * {@link WireMetricsBuilder#withScheduledTaskCountMetricEnabled(boolean)} or set a capacity that is not unlimited
     * via {@link WireBuilder#withScheduledTaskCapacity(long)}.
     */
    long getUnprocessedTaskCount();
}

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

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.StandardWiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A wiring model is a collection of wires. It can be used to analyze the wiring of a system and to generate diagrams.
 */
public abstract class WiringModel {

    private final PlatformContext platformContext;
    private final Time time;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    protected WiringModel(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        this.platformContext = Objects.requireNonNull(platformContext);
        this.time = Objects.requireNonNull(time);
    }

    /**
     * Build a new wiring model instance.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     * @return a new wiring model instance
     */
    @NonNull
    public static WiringModel create(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        return new StandardWiringModel(platformContext, time);
    }

    /**
     * Get a new wire builder.
     *
     * @param name the name of the wire. Used for metrics and debugging. Must be unique (not enforced by framework).
     *             Must only contain alphanumeric characters, underscores, and hyphens (enforced by framework).
     * @return a new wire builder
     */
    @NonNull
    public final <O> WireBuilder<O> wireBuilder(@NonNull final String name) {
        return new WireBuilder<>(this, name);
    }

    /**
     * Get a new wire metrics builder. Can be passed to {@link WireBuilder#withMetricsBuilder(WireMetricsBuilder)} to
     * add metrics to the wire.
     *
     * @return a new wire metrics builder
     */
    @NonNull
    public final WireMetricsBuilder metricsBuilder() {
        return new WireMetricsBuilder(platformContext.getMetrics(), time);
    }

    /**
     * Check to see if there is cyclic backpressure in the wiring model. Cyclical back pressure can lead to deadlocks,
     * and so it should be avoided at all costs.
     *
     * <p>
     * If this method finds cyclical backpressure, it will log a message that will fail standard platform tests.
     *
     * @return true if there is cyclical backpressure, false otherwise
     */
    public abstract boolean checkForCyclicalBackpressure();

    /**
     * Generate a mermaid style wiring diagram.
     *
     * @return a mermaid style wiring diagram
     */
    @NonNull
    public abstract String generateWiringDiagram();

    /**
     * Reserved for internal framework use. Do not call this method directly.
     * <p>
     * Register a vertex in the wiring model. These are either Wires or WireTransformers. Vertexes always have a single
     * Java object output type, although there may be many consumers of that output. Vertexes may have many input
     * types.
     *
     * @param vertexName the name of the vertex
     */
    public abstract void registerVertex(@NonNull String vertexName);

    /**
     * Reserved for internal framework use. Do not call this method directly.
     * <p>
     * Register an edge between two vertices.
     *
     * @param originVertex      the origin vertex
     * @param destinationVertex the destination vertex
     * @param label             the label of the edge
     */
    public abstract void registerEdge(
            @NonNull String originVertex, @NonNull String destinationVertex, @NonNull String label);
}

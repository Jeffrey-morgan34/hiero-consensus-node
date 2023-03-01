/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.metrics.auxiliary;

import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.AbstractMetricWrapper;
import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.AbstractMetricConfigBuilder;
import com.swirlds.common.metrics.MetricsFactory;

/**
 * A metric that tracks the maximum value of an integer.
 */
public class MaxIntegerMetric extends AbstractMetricWrapper<IntegerAccumulator> {

    public MaxIntegerMetric(final MetricsFactory factory, final Config config) {
        super(factory, new IntegerAccumulator.ConfigBuilder(config).build());
    }

    public int get() {
        return getWrapped().get();
    }

    public void update(final int value) {
        getWrapped().update(value);
    }


    /**
     * A configuration for a {@link MaxIntegerMetric}.
     */
    public record Config(
            String category,
            String name,
            String description,
            String unit,
            String format
    ) implements MetricConfig<MaxIntegerMetric> {
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(unit, "unit");
            throwArgBlank(format, "format");
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<MaxIntegerMetric> getResultClass() {
            return MaxIntegerMetric.class;
        }

        @Override
        public MaxIntegerMetric create(MetricsFactory factory) {
            return new MaxIntegerMetric(factory, this);
        }
    }


    /**
     * A builder for {@link Config} objects.
     */
    public static class ConfigBuilder extends AbstractMetricConfigBuilder<MaxIntegerMetric, Config, ConfigBuilder> {

        public ConfigBuilder(String category, String name) {
            super(category, name);
        }

        public ConfigBuilder(MetricConfig<?> config) {
            super(config);
        }

        @Override
        public ConfigBuilder withUnit(String unit) {
            return super.withUnit(unit);
        }

        @Override
        public ConfigBuilder withFormat(String format) {
            return super.withFormat(format);
        }

        @Override
        public Config build() {
            return new Config(getCategory(), getName(), getDescription(), getUnit(), getFormat());
        }

        @Override
        protected ConfigBuilder self() {
            return this;
        }
    }

}

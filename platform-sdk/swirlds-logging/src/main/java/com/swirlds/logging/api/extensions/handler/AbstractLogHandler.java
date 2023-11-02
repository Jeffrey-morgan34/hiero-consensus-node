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

package com.swirlds.logging.api.extensions.handler;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.level.LoggingLevelConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * An abstract log handler. This class provides some basic functionality that is used by all log handlers.
 */
public abstract class AbstractLogHandler implements LogHandler {

    /**
     * The configuration key of the log handler. This is used to create configuration keys for the log handler.
     */
    private final String configKey;

    /**
     * The configuration.
     */
    private final Configuration configuration;

    /**
     * The logging level configuration. This is used to define specific logging levels for logger names.
     */
    private final LoggingLevelConfig loggingLevelConfig;

    /**
     * Creates a new log handler.
     *
     * @param configKey     the configuration key
     * @param configuration the configuration
     */
    public AbstractLogHandler(@NonNull final String configKey, @NonNull final Configuration configuration) {
        this.configKey = Objects.requireNonNull(configKey, "configKey must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.loggingLevelConfig = new LoggingLevelConfig(configuration, "logging.handler." + configKey + ".level");
    }

    @Override
    public boolean isActive() {
        return configuration.getValue("logging.handler." + configKey + ".enabled", Boolean.class, false);
    }

    @Override
    public boolean isEnabled(String name, Level level) {
        return loggingLevelConfig.isEnabled(name, level);
    }

    /**
     * Returns the configuration
     *
     * @return the configuration
     */
    @NonNull
    protected Configuration getConfiguration() {
        return configuration;
    }
}

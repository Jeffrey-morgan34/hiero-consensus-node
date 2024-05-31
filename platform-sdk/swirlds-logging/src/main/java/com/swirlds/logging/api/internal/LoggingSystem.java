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

package com.swirlds.logging.api.internal;

import com.swirlds.base.internal.BaseExecutorFactory;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import com.swirlds.logging.api.extensions.provider.LogProvider;
import com.swirlds.logging.api.extensions.provider.LogProviderFactory;
import com.swirlds.logging.api.internal.event.SimpleLogEventFactory;
import com.swirlds.logging.api.internal.level.LoggingSystemConfig;
import com.swirlds.logging.api.internal.level.LoggingSystemConfigFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The implementation of the logging system.
 */
public class LoggingSystem implements LogEventConsumer {
    private static final int FLUSH_FREQUENCY = 1000;
    private static final String LOGGING_HANDLER_PREFIX = "logging.handler.";
    private static final int LOGGING_HANDLER_PREFIX_LENGTH = LOGGING_HANDLER_PREFIX.length();
    private static final String LOGGING_HANDLER_TYPE = LOGGING_HANDLER_PREFIX + "%s.type";

    /**
     * The emergency logger that is used to log errors that occur during the logging process.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The name of the root logger.
     */
    public static final String ROOT_LOGGER_NAME = "";

    /**
     * The Configuration object
     */
    private final Configuration configuration;

    /**
     * The handlers of the logging system.
     */
    private final List<LogHandler> handlers;

    /**
     * The handlers of the logging system.
     */
    private final List<LogHandler> extraHandlers = new ArrayList<>();

    /**
     * The already created loggers of the logging system.
     */
    private final Map<String, LoggerImpl> loggers;

    /**
     * The level configuration of the logging system that checks if a specific logger is enabled for a specific level.
     */
    private LoggingSystemConfig levelConfig;

    /**
     * The factory that is used to create log events.
     */
    private final LogEventFactory logEventFactory = new SimpleLogEventFactory();

    private final Collection<LogEventConsumer> mirrors = new ArrayList<>();

    /**
     * Creates a new logging system.
     *
     * @param configuration the configuration of the logging system
     */
    public LoggingSystem(@NonNull final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.loggers = new ConcurrentHashMap<>();
        this.handlers = LoggingSystemConfigFactory.createLogHandlers(configuration);
        this.levelConfig = LoggingSystemConfigFactory.createLoggingSystemConfig(configuration, handlers);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAndFinalize)); // makes sure all things get to disk
        BaseExecutorFactory.getInstance() // Add a forced flush for the handlers at regular cadence
                .scheduleAtFixedRate(this::flushHandlers, FLUSH_FREQUENCY, FLUSH_FREQUENCY, TimeUnit.MILLISECONDS);
    }

    /**
     * Flush all active handlers.
     * Logs to the {@code EMERGENCY_LOGGER} in case of error
     */
    private void flushHandlers() {
        this.handlers.forEach(h -> {
            try {
                if (h.isActive()) {
                    h.flush();
                }
            } catch (Exception e) {
                EMERGENCY_LOGGER.log(Level.WARN, "Unexpected error flushing " + h.getName(), e);
            }
        });
    }
    /**
     * Updates the logging system with the given configuration.
     *
     * @param configuration the configuration to update the logging system with
     * @implNote Currently only the level and marker configuration is updated. New handlers are not added and existing
     * handlers are not removed for now.
     */
    public void update(final @NonNull Configuration configuration) {
        // TODO: review if we need to create new handlers with the update method. if so,
        // we need to make sure that we dont throw away previous existent handlers
        //  and that we flush removed ones
        LoggingSystemConfig value = LoggingSystemConfigFactory.createLoggingSystemConfig(configuration, this.handlers);
        synchronized (this) {
            this.levelConfig = value;
        }
    }

    /**
     * Adds a new handler to the logging system.
     *
     * @param handler the handler to add
     */
    public void addHandler(@NonNull final LogHandler handler) {
        // TODO: Review if this approach keeps making sense
    }

    /**
     * Removes a handler from the logging system.
     *
     * @param handler the handler to remove
     */
    public void removeHandler(@NonNull final LogHandler handler) {
        // TODO: Review if this approach keeps making sense

    }

    /**
     * Returns the logger with the given name.
     *
     * @param name the name of the logger
     * @return the logger with the given name
     */
    @NonNull
    public LoggerImpl getLogger(@NonNull final String name) {
        if (name == null) {
            EMERGENCY_LOGGER.logNPE("name");
            return loggers.computeIfAbsent(ROOT_LOGGER_NAME, n -> new LoggerImpl(n, logEventFactory, this));
        }
        return loggers.computeIfAbsent(name.trim(), n -> new LoggerImpl(n, logEventFactory, this));
    }

    /**
     * Checks if the logger with the given name is enabled for the given level.
     *
     * @param name  the name of the logger
     * @param level the level to check
     * @return true, if the logger with the given name is enabled for the given level, otherwise false
     */
    public boolean isEnabled(@NonNull final String name, @NonNull final Level level, @Nullable final Marker marker) {
        if (name == null) {
            EMERGENCY_LOGGER.logNPE("name");
            return isEnabled(ROOT_LOGGER_NAME, level, marker);
        }
        if (level == null) {
            EMERGENCY_LOGGER.logNPE("level");
            return true;
        }
        if (marker == null) { // favor inlining
            return levelConfig.isEnabled(name, level);
        } else {
            return levelConfig.isEnabled(name, level, marker);
        }
    }

    /**
     * Process the event if any of the handlers is able to handle it
     *
     * @param event the event to process
     */
    public void accept(@NonNull final LogEvent event) {
        if (event == null) {
            EMERGENCY_LOGGER.logNPE("event");
            return;
        }
        try {
            if (handlers.isEmpty() /*ALSO CHECK if the root log level can log this*/) {
                EMERGENCY_LOGGER.log(event);
                return;
            }

            Set<Integer> handlerIndexes = event.marker() != null
                    ? this.levelConfig.getHandlers(event.loggerName(), event.level(), event.marker())
                    : this.levelConfig.getHandlers(event.loggerName(), event.level());

            for (Integer logHandlerIndex : handlerIndexes) {
                LogHandler logHandler = this.handlers.get(logHandlerIndex);
                try {
                    logHandler.handle(event);
                } catch (final Throwable throwable) {
                    EMERGENCY_LOGGER.log(
                            Level.ERROR,
                            "Exception in handling log event by logHandler "
                                    + logHandler.getClass().getName(),
                            throwable);
                }
            }

        } catch (final Throwable throwable) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Exception in handling log event", throwable);
        } finally {
            if (!mirrors.isEmpty())
                try {
                    for (LogEventConsumer mirror : mirrors) {
                        mirror.accept(event);
                    }
                } catch (Exception e) {
                    // ignore
                }
        }
    }

    /**
     * Loads all {@link LogProviderFactory} instances by SPI / {@link ServiceLoader} and installs them into the logging
     * system.
     */
    @Deprecated
    // TODO: do we still need the providers approach?
    public void installProviders() {
        final ServiceLoader<LogProviderFactory> serviceLoader = ServiceLoader.load(LogProviderFactory.class);
        final List<LogProvider> providers = serviceLoader.stream()
                .map(Provider::get)
                .map(factory -> factory.create(configuration))
                .filter(LogProvider::isActive)
                .toList();
        providers.forEach(p -> p.install(getLogEventFactory(), this));
        EMERGENCY_LOGGER.log(Level.DEBUG, providers.size() + " logging providers installed: " + providers);
    }

    /**
     * Loads all {@link LogHandlerFactory} instances by SPI / {@link ServiceLoader} and installs them into the logging
     * system.
     */
    @Deprecated
    public void installHandlers() {
        // TODO this can be replaced with the update method.

    }

    /**
     * Stops and finalizes the logging system.
     */
    public void stopAndFinalize() {
        handlers.forEach(LogHandler::stopAndFinalize);
    }

    /**
     * Returns the log event factory of the logging system.
     *
     * @return the log event factory of the logging system
     */
    @NonNull
    public LogEventFactory getLogEventFactory() {
        return logEventFactory;
    }

    /**
     * Returns the handler list for testing purposes
     *
     * @return handler list
     */
    @NonNull
    public List<LogHandler> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }

    public void addMirror(@NonNull final LogEventConsumer other) {
        if (other == null) {
            EMERGENCY_LOGGER.logNPE("handler");
        } else {
            this.mirrors.add(other);
        }
    }

    /**
     * Removes a handler from the logging system.
     *
     * @param handler the handler to remove
     */
    public void removeMirror(@NonNull final LogEventConsumer handler) {
        // TODO: Review if this approach keeps making sense
        if (handler == null) {
            EMERGENCY_LOGGER.logNPE("handler");
        } else {
            mirrors.remove(handler);
        }
    }
}

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

package com.swirlds.logging.test.fixtures.internal;

import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.DefaultLoggingSystem;
import com.swirlds.logging.test.fixtures.LoggingMirror;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class LoggingMirrorImpl extends AbstractLoggingMirror implements LogHandler {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    public LoggingMirrorImpl() {
        DefaultLoggingSystem.getInstance().addHandler(this);
    }

    @Override
    public void accept(LogEvent event) {
        events.add(event);
    }

    @Override
    public void dispose() {
        DefaultLoggingSystem.getInstance().removeHandler(this);
    }

    @Override
    protected LoggingMirror filter(Function<LogEvent, Boolean> filter) {
        return new FilteredLoggingMirror(events, filter, this::dispose);
    }

    @Override
    public List<LogEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    @NonNull
    @Override
    public String getName() {
        return "LoggingMirror";
    }
}

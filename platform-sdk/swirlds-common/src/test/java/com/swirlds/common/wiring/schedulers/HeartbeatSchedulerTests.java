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

package com.swirlds.common.wiring.schedulers;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class HeartbeatSchedulerTests {

    @Test
    void heartbeatByFrequencyTest() throws InterruptedException {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime fakeTime = new FakeTime();
        final WiringModel model = WiringModel.create(platformContext, fakeTime);

        final OutputWire<Instant> outputWire = model.buildHeartbeatWire(100);

        final AtomicLong counter = new AtomicLong(0);
        outputWire.solderTo("counter", (time) -> {
            assertEquals(time, fakeTime.now());
            counter.incrementAndGet();
        });

        model.start();
        SECONDS.sleep(1);
        model.stop();

        // Exact timer rate is not guaranteed. Validate that it's within 50% of the expected rate.
        // Experimentally, I tend to see results in the region of 101. But making the assertion stricter
        // may result in a flaky test.
        assertTrue(counter.get() > 50 && counter.get() < 150, "counter=" + counter.get());
    }

    @Test
    void heartbeatByPeriodTest() throws InterruptedException {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime fakeTime = new FakeTime();
        final WiringModel model = WiringModel.create(platformContext, fakeTime);

        final OutputWire<Instant> outputWire = model.buildHeartbeatWire(Duration.ofMillis(10));

        final AtomicLong counter = new AtomicLong(0);
        outputWire.solderTo("counter", (time) -> {
            assertEquals(time, fakeTime.now());
            counter.incrementAndGet();
        });

        model.start();
        SECONDS.sleep(1);
        model.stop();

        // Exact timer rate is not guaranteed. Validate that it's within 50% of the expected rate.
        // Experimentally, I tend to see results in the region of 101. But making the assertion stricter
        // may result in a flaky test.
        assertTrue(counter.get() > 50 && counter.get() < 150, "counter=" + counter.get());
    }

    @Test
    void heartbeatsAtDifferentRates() throws InterruptedException {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime fakeTime = new FakeTime();
        final WiringModel model = WiringModel.create(platformContext, fakeTime);

        final OutputWire<Instant> outputWireA = model.buildHeartbeatWire(100);
        final OutputWire<Instant> outputWireB = model.buildHeartbeatWire(Duration.ofMillis(5));
        final OutputWire<Instant> outputWireC = model.buildHeartbeatWire(Duration.ofMillis(50));

        final AtomicLong counterA = new AtomicLong(0);
        outputWireA.solderTo("counterA", (time) -> {
            assertEquals(time, fakeTime.now());
            counterA.incrementAndGet();
        });

        final AtomicLong counterB = new AtomicLong(0);
        outputWireB.solderTo("counterB", (time) -> {
            assertEquals(time, fakeTime.now());
            counterB.incrementAndGet();
        });

        final AtomicLong counterC = new AtomicLong(0);
        outputWireC.solderTo("counterC", (time) -> {
            assertEquals(time, fakeTime.now());
            counterC.incrementAndGet();
        });

        model.start();
        SECONDS.sleep(1);
        model.stop();

        // Exact timer rate is not guaranteed. Validate that it's within 50% of the expected rate.
        // Experimentally, I tend to see results in the region of 101, 202, and 21. But making the assertion stricter
        // may result in a flaky test.
        assertTrue(counterA.get() > 50 && counterA.get() < 150, "counter=" + counterA.get());
        assertTrue(counterB.get() > 100 && counterB.get() < 300, "counter=" + counterB.get());
        assertTrue(counterC.get() > 10 && counterC.get() < 30, "counter=" + counterC.get());
    }
}

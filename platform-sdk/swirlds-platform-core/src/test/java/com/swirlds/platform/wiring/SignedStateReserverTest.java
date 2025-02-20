// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.component.framework.model.WiringModel;
import com.swirlds.component.framework.model.WiringModelBuilder;
import com.swirlds.component.framework.schedulers.TaskScheduler;
import com.swirlds.component.framework.schedulers.builders.TaskSchedulerType;
import com.swirlds.component.framework.wires.input.BindableInputWire;
import com.swirlds.component.framework.wires.output.OutputWire;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.state.MerkleNodeState;
import com.swirlds.platform.state.service.PlatformStateFacade;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SignedStateReserverTest {

    @Test
    void basicTest() {
        final int numConsumers = 3;

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        MerkleNodeState mock = mock(MerkleNodeState.class);
        final SignedState signedState = new SignedState(
                platformContext.getConfiguration(),
                mock(SignatureVerifier.class),
                mock,
                "create",
                false,
                false,
                false,
                mock(PlatformStateFacade.class));

        final WiringModel model = WiringModelBuilder.create(platformContext).build();
        final TaskScheduler<ReservedSignedState> taskScheduler = model.<ReservedSignedState>schedulerBuilder(
                        "scheduler")
                .withType(TaskSchedulerType.DIRECT)
                .build();
        final OutputWire<ReservedSignedState> outputWire =
                taskScheduler.getOutputWire().buildAdvancedTransformer(new SignedStateReserver("reserver"));
        final BindableInputWire<ReservedSignedState, ReservedSignedState> inputWire =
                taskScheduler.buildInputWire("in");
        inputWire.bind(s -> s);

        final List<ValueReference<ReservedSignedState>> consumers = Stream.generate(
                        ValueReference<ReservedSignedState>::new)
                .limit(numConsumers)
                .toList();
        IntStream.range(0, consumers.size())
                .forEach(i -> outputWire.solderTo("name_" + i, "consumer input", consumers.get(i)::setValue));

        final ReservedSignedState state = signedState.reserve("main");
        assertFalse(state.isClosed(), "we just reserved it, so it should not be closed");
        assertEquals(1, signedState.getReservationCount(), "the reservation count should be 1");
        inputWire.put(state);
        assertTrue(state.isClosed(), "the reserver should have closed our reservation");
        consumers.forEach(c -> assertFalse(c.getValue().isClosed(), "the consumer should not have closed its state"));
        assertEquals(
                numConsumers, signedState.getReservationCount(), "there should be a reservation for each consumer");
        consumers.forEach(c -> c.getValue().close());
    }
}

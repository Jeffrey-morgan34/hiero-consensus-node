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

package com.hedera.node.app.workflows.handle.flow.txn.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.state.WorkingStateAccessor;
import com.swirlds.state.HederaState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateModuleTest {
    @Mock
    private WorkingStateAccessor workingStateAccessor;

    @Mock
    private HederaState hederaState;

    @Test
    void getsStateFromAccessor() {
        given(workingStateAccessor.getHederaState()).willReturn(hederaState);

        assertThat(StateModule.provideHederaState(workingStateAccessor)).isSameAs(hederaState);
    }

    @Test
    void providesStackImpl() {
        assertThat(StateModule.provideSavepointStackImpl(hederaState)).isNotNull();
    }

    @Test
    void providesReadableStoreFactory() {
        assertThat(StateModule.provideReadableStoreFactory(StateModule.provideSavepointStackImpl(hederaState)))
                .isNotNull();
    }
}

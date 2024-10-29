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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.setunlimitedautoassociations;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.setunlimitedautoassociations.SetUnlimitedAutoAssociationsTranslator.SET_UNLIMITED_AUTO_ASSOC;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHasAttemptWithSelectorAndCustomConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.setunlimitedautoassociations.SetUnlimitedAutoAssociationsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.setunlimitedautoassociations.SetUnlimitedAutoAssociationsTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetUnlimitedAutoAssociationsTranslatorTest {

    @Mock
    private HasCallAttempt attempt;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private HederaNativeOperations nativeOperations;

    @Mock
    private Configuration configuration;

    @Mock
    private ContractsConfig contractsConfig;

    private SetUnlimitedAutoAssociationsTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new SetUnlimitedAutoAssociationsTranslator();
    }

    @Test
    void matchesWhenEnabled() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractSetUnlimitedAutoAssociationsEnabled())
                .willReturn(true);
        attempt = prepareHasAttemptWithSelectorAndCustomConfig(
                SET_UNLIMITED_AUTO_ASSOC,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesWhenDisabled() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.systemContractSetUnlimitedAutoAssociationsEnabled())
                .willReturn(false);
        attempt = prepareHasAttemptWithSelectorAndCustomConfig(
                SET_UNLIMITED_AUTO_ASSOC,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);
        assertFalse(subject.matches(attempt));
    }

    @Test
    void callFromWithTrueValue() {
        final var inputBytes = SET_UNLIMITED_AUTO_ASSOC.encodeCallWithArgs(true);
        given(attempt.inputBytes()).willReturn(inputBytes.array());
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(SetUnlimitedAutoAssociationsCall.class);
    }

    @Test
    void callFromWithFalseValue() {
        final var inputBytes = SET_UNLIMITED_AUTO_ASSOC.encodeCallWithArgs(false);
        given(attempt.inputBytes()).willReturn(inputBytes.array());
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(SetUnlimitedAutoAssociationsCall.class);
    }
}

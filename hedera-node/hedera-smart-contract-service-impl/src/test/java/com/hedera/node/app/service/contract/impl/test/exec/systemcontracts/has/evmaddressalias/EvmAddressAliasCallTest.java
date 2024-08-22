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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.evmaddressalias;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ALIASED_RECEIVER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ALIASED_SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.B_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OPERATOR;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RECEIVER_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.revertOutputFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.getevmaddressalias.EvmAddressAliasCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.getevmaddressalias.EvmAddressAliasTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance.HbarAllowanceTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class EvmAddressAliasCallTest extends CallTestBase {
    private EvmAddressAliasCall subject;

    @Mock
    private HasCallAttempt attempt;

    @Mock
    private com.hedera.hapi.node.state.token.Account account;

    @Test
    void invalidAccountIdWhenNotLongZero() {
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        subject = new EvmAddressAliasCall(attempt, APPROVED_HEADLONG_ADDRESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(EvmAddressAliasTranslator.EVM_ADDRESS_ALIAS
                        .getOutputs()
                        .encodeElements((long)com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID.protoOrdinal(),
                                ZERO_ADDRESS)
                        .array()),
                result.getOutput());
    }

    @Test
    void invalidAccountIdWhenNoAccount() {
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        subject = new EvmAddressAliasCall(attempt, asHeadlongAddress(NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS.toArray()));

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(EvmAddressAliasTranslator.EVM_ADDRESS_ALIAS
                        .getOutputs()
                        .encodeElements((long)com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID.protoOrdinal(),
                                ZERO_ADDRESS)
                        .array()),
                result.getOutput());
    }

    @Test
    void invalidAccountIdWhenNoAlias() {
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(nativeOperations.getAccount(numberOfLongZero(NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS))).willReturn(OPERATOR);
        subject = new EvmAddressAliasCall(attempt, asHeadlongAddress(NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS.toArray()));

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(EvmAddressAliasTranslator.EVM_ADDRESS_ALIAS
                        .getOutputs()
                        .encodeElements((long)com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID.protoOrdinal(),
                                ZERO_ADDRESS)
                        .array()),
                result.getOutput());
    }

    @Test
    void successfulCall() {
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(nativeOperations.getAccount(numberOfLongZero(NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS))).willReturn(account);
        given(account.hasAccountId()).willReturn(true);
        given(account.accountId()).willReturn(ALIASED_RECEIVER_ID);
        subject = new EvmAddressAliasCall(attempt, asHeadlongAddress(NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS.toArray()));

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(EvmAddressAliasTranslator.EVM_ADDRESS_ALIAS
                        .getOutputs()
                        .encodeElements((long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                asHeadlongAddress(RECEIVER_ADDRESS.toByteArray()))
                        .array()),
                result.getOutput());
    }
}

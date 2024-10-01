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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AN_ED25519_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_SECP256K1_KEY;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy.Decision;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.key.KeyVerifier;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleSystemContractOperationsTest {

    @Mock
    private HandleContext context;

    @Mock
    private ContractCallStreamBuilder recordBuilder;

    @Mock
    private ExchangeRateInfo exchangeRateInfo;

    @Mock
    private VerificationStrategy strategy;

    @Mock
    private SignatureVerification passed;

    @Mock
    private SignatureVerification failed;

    @Mock
    private KeyVerifier keyVerifier;

    @Mock
    private HandleContext.SavepointStack savepointStack;

    private HandleSystemContractOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleSystemContractOperations(context, A_SECP256K1_KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesRespectingGivenStrategy() {
        final var captor = ArgumentCaptor.forClass(Predicate.class);
        given(strategy.decideForPrimitive(TestHelpers.A_CONTRACT_KEY)).willReturn(Decision.VALID);
        given(strategy.decideForPrimitive(AN_ED25519_KEY)).willReturn(Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);
        given(strategy.decideForPrimitive(TestHelpers.B_SECP256K1_KEY))
                .willReturn(Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);
        given(strategy.decideForPrimitive(TestHelpers.A_SECP256K1_KEY)).willReturn(Decision.INVALID);
        given(passed.passed()).willReturn(true);
        given(context.keyVerifier()).willReturn(keyVerifier);
        given(keyVerifier.verificationFor(AN_ED25519_KEY)).willReturn(passed);
        given(keyVerifier.verificationFor(TestHelpers.B_SECP256K1_KEY)).willReturn(failed);
        doCallRealMethod().when(strategy).asSignatureTestIn(context, A_SECP256K1_KEY);

        subject.dispatch(TransactionBody.DEFAULT, strategy, A_NEW_ACCOUNT_ID, CryptoTransferStreamBuilder.class);

        verify(context)
                .dispatchChildTransaction(
                        eq(TransactionBody.DEFAULT),
                        eq(CryptoTransferStreamBuilder.class),
                        captor.capture(),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(CHILD),
                        any());
        final var test = captor.getValue();
        assertTrue(test.test(TestHelpers.A_CONTRACT_KEY));
        assertTrue(test.test(AN_ED25519_KEY));
        assertFalse(test.test(TestHelpers.A_SECP256K1_KEY));
        assertFalse(test.test(TestHelpers.B_SECP256K1_KEY));
    }

    @Test
    void externalizeSuccessfulResultWithTransactionBodyTest() {
        var transaction = Transaction.newBuilder()
                .body(TransactionBody.newBuilder()
                        .transactionID(TransactionID.DEFAULT)
                        .build())
                .build();
        var contractFunctionResult = SystemContractUtils.successResultOfZeroValueTraceable(
                0,
                org.apache.tuweni.bytes.Bytes.EMPTY,
                100L,
                org.apache.tuweni.bytes.Bytes.EMPTY,
                AccountID.newBuilder().build());

        // given
        given(context.savepointStack()).willReturn(savepointStack);
        given(savepointStack.addChildRecordBuilder(ContractCallStreamBuilder.class, CONTRACT_CALL))
                .willReturn(recordBuilder);
        given(recordBuilder.transaction(transaction)).willReturn(recordBuilder);
        given(recordBuilder.status(ResponseCodeEnum.SUCCESS)).willReturn(recordBuilder);

        // when
        subject.externalizeResult(contractFunctionResult, ResponseCodeEnum.SUCCESS, transaction);

        // then
        verify(recordBuilder).status(ResponseCodeEnum.SUCCESS);
        verify(recordBuilder).contractCallResult(contractFunctionResult);
    }

    @Test
    void syntheticTransactionForHtsCallTest() {
        assertNotNull(subject.syntheticTransactionForNativeCall(Bytes.EMPTY, ContractID.DEFAULT, true));
    }

    @Test
    void currentExchangeRateTest() {
        given(context.exchangeRateInfo()).willReturn(exchangeRateInfo);
        subject.currentExchangeRate();
        verify(context).exchangeRateInfo();
        verify(exchangeRateInfo).activeRate(any());
    }
}

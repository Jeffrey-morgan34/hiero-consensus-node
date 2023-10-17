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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

public class ERCGrantApprovalCall extends AbstractGrantApprovalCall {

    public ERCGrantApprovalCall(
            @NonNull final Enhancement enhancement,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID sender,
            @NonNull final TokenID token,
            @NonNull final AccountID spender,
            @NonNull final BigInteger amount,
            @NonNull final TokenType tokenType) {
        super(enhancement, verificationStrategy, sender, token, spender, amount, tokenType);
    }

    @NonNull
    @Override
    public PricedResult execute() {
        // TODO - gas calculation
        if (token == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var recordBuilder = systemContractOperations()
                .dispatch(callGrantApproval(), verificationStrategy, sender, SingleTransactionRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            final var encodedOutput = tokenType.equals(TokenType.FUNGIBLE_COMMON)
                    ? GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)
                    : GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT
                            .getOutputs()
                            .encodeElements();
            return gasOnly(successResult(encodedOutput, 0L));
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support.translators.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Translates a ethereum transaction into a {@link SingleTransactionRecord}.
 */
public class EthereumTransactionTranslator implements BlockTransactionPartsTranslator {
    private static final Set<ResponseCodeEnum> ABORT_STATUSES = EnumSet.of(INSUFFICIENT_TX_FEE);

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> {
            parts.outputIfPresent(TransactionOutput.TransactionOneOfType.ETHEREUM_CALL)
                    .map(TransactionOutput::ethereumCallOrThrow)
                    .ifPresent(ethTxOutput -> {
                        recordBuilder.ethereumHash(ethTxOutput.ethereumHash());
                        final var result =
                                switch (ethTxOutput.ethResult().kind()) {
                                        // CONSENSUS_GAS_EXHAUSTED
                                    case UNSET -> ContractFunctionResult.DEFAULT;
                                    case ETHEREUM_CALL_RESULT -> {
                                        final var callResult = ethTxOutput.ethereumCallResultOrThrow();
                                        recordBuilder.contractCallResult(callResult);
                                        yield callResult;
                                    }
                                    case ETHEREUM_CREATE_RESULT -> {
                                        final var createResult = ethTxOutput.ethereumCreateResultOrThrow();
                                        recordBuilder.contractCreateResult(createResult);
                                        yield createResult;
                                    }
                                };
                        if (result.gasUsed() > 0L) {
                            receiptBuilder.contractID(result.contractID());
                        }
                    });
        });
    }
}

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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.hapi.streams.SidecarType.CONTRACT_STATE_CHANGE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.HAPI_RECORD_BUILDER_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.PENDING_CREATION_BUILDER_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.PROPAGATED_CALL_FAILURE_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.SYSTEM_CONTRACT_GAS_CALCULATOR_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.TINYBAR_VALUES_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.TRACKER_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Infrastructure component that builds the initial {@link MessageFrame} instance for a transaction.
 * This includes tasks like,
 * <ol>
 *     <li>Putting the {@link Configuration} in the frame context variables.</li>
 *     <li>Setting the gas price and block values from the {@link HederaEvmContext}.</li>
 *     <li>Setting input data and code based on the message call type.</li>
 * </ol>
 */
@Singleton
public class FrameBuilder {
    private static final int MAX_STACK_SIZE = 1024;

    @Inject
    public FrameBuilder() {
        // Dagger2
    }

    /**
     * Builds the initial {@link MessageFrame} instance for a transaction.
     *
     * @param transaction the transaction
     * @param worldUpdater the world updater for the transaction
     * @param context the Hedera EVM context (gas price, block values, etc.)
     * @param config the active Hedera configuration
     * @param from the sender of the transaction
     * @param to the recipient of the transaction
     * @param intrinsicGas the intrinsic gas cost, needed to calculate remaining gas
     * @return the initial frame
     */
    @SuppressWarnings("java:S107")
    public MessageFrame buildInitialFrameWith(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmContext context,
            @NonNull final Configuration config,
            @NonNull final FeatureFlags featureFlags,
            @NonNull final Address from,
            @NonNull final Address to,
            final long intrinsicGas) {
        final var value = transaction.weiValue();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final var nominalCoinbase = asLongZeroAddress(ledgerConfig.fundingAccount());
        final var contextVariables = contextVariablesFrom(config, context);
        final var builder = MessageFrame.builder()
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(worldUpdater.updater())
                .initialGas(transaction.gasAvailable(intrinsicGas))
                .originator(from)
                .gasPrice(Wei.of(context.gasPrice()))
                .sender(from)
                .value(value)
                .apparentValue(value)
                .blockValues(context.blockValuesOf(transaction.gasLimit()))
                .completer(unused -> {})
                .isStatic(context.staticCall())
                .miningBeneficiary(nominalCoinbase)
                .blockHashLookup(context.blocks()::blockHashOf)
                .contextVariables(contextVariables);
        if (transaction.isCreate()) {
            return finishedAsCreate(to, builder, transaction);
        } else {
            return finishedAsCall(to, worldUpdater, builder, transaction, featureFlags, config);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contextVariablesFrom(
            @NonNull final Configuration config, @NonNull final HederaEvmContext context) {
        final Map<String, Object> contextEntries = new HashMap<>();
        contextEntries.put(CONFIG_CONTEXT_VARIABLE, config);
        contextEntries.put(TINYBAR_VALUES_CONTEXT_VARIABLE, context.tinybarValues());
        contextEntries.put(SYSTEM_CONTRACT_GAS_CALCULATOR_CONTEXT_VARIABLE, context.systemContractGasCalculator());
        contextEntries.put(PROPAGATED_CALL_FAILURE_CONTEXT_VARIABLE, new PropagatedCallFailureRef());
        if (config.getConfigData(ContractsConfig.class).sidecars().contains(CONTRACT_STATE_CHANGE)) {
            contextEntries.put(TRACKER_CONTEXT_VARIABLE, new StorageAccessTracker());
        }
        if (context.isTransaction()) {
            contextEntries.put(HAPI_RECORD_BUILDER_CONTEXT_VARIABLE, context.recordBuilder());
            contextEntries.put(
                    PENDING_CREATION_BUILDER_CONTEXT_VARIABLE, context.pendingCreationRecordBuilderReference());
        }
        return contextEntries;
    }

    private MessageFrame finishedAsCreate(
            @NonNull final Address to,
            @NonNull final MessageFrame.Builder builder,
            @NonNull final HederaEvmTransaction transaction) {
        return builder.type(MessageFrame.Type.CONTRACT_CREATION)
                .address(to)
                .contract(to)
                .inputData(Bytes.EMPTY)
                .code(CodeFactory.createCode(transaction.evmPayload(), 0, false))
                .build();
    }

    private MessageFrame finishedAsCall(
            @NonNull final Address to,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final MessageFrame.Builder builder,
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final FeatureFlags featureFlags,
            @NonNull final Configuration config) {
        Code code = CodeV0.EMPTY_CODE;
        final var contractId = transaction.contractIdOrThrow();

        if (canLoadCodeFromAccount(transaction, worldUpdater, contractId, config, featureFlags)) {
            final var account = worldUpdater.getHederaAccount(to);
            if (account == null && contractMustBePresent(config, featureFlags, contractId)) {
                validateTrue(transaction.permitsMissingContract(), INVALID_ETHEREUM_TRANSACTION);
            } else {
                code = account.getEvmCode();
                validateTrue(
                        emptyCodePossiblyAllowed(config, featureFlags, contractId, transaction, code),
                        INVALID_CONTRACT_ID);
            }
        }
        return builder.type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(transaction.evmPayload())
                .code(code)
                .build();
    }

    private boolean canLoadCodeFromAccount(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final ContractID contractId,
            @NonNull final Configuration config,
            @NonNull final FeatureFlags featureFlags) {
        requireNonNull(transaction);
        requireNonNull(worldUpdater);

        // If the contract is deleted, never load code from it.
        final var contract = worldUpdater
                .enhancement()
                .nativeOperations()
                .readableAccountStore()
                .getContractById(contractId);
        if (contract != null && contract.deleted()) {
            return false;
        }

        return worldUpdater.getHederaAccount(contractId) != null
                || contractMustBePresent(config, featureFlags, contractId);
    }

    private boolean contractMustBePresent(
            @NonNull final Configuration config,
            @NonNull final FeatureFlags featureFlags,
            @NonNull final ContractID contractID) {
        final var possiblyGrandFatheredEntityNumOf = contractID.hasContractNum() ? contractID.contractNum() : null;
        return !featureFlags.isAllowCallsToNonContractAccountsEnabled(config, possiblyGrandFatheredEntityNumOf);
    }

    private boolean emptyCodePossiblyAllowed(
            @NonNull final Configuration config,
            @NonNull final FeatureFlags featureFlags,
            @NonNull final ContractID contractId,
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final Code code) {
        return !(contractMustBePresent(config, featureFlags, contractId) && code.equals(CodeV0.EMPTY_CODE))
                || transaction.isEthereumTransaction()
                || transaction.hasValue();
    }
}

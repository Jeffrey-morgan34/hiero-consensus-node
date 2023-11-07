/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.contracts.operations;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.ExtCodeSizeOperation;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Hedera adapted version of the {@link ExtCodeSizeOperation}.
 *
 * <p>Performs an existence check on the requested {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist or it is deleted.
 */
public class HederaExtCodeSizeOperationV045 extends ExtCodeSizeOperation {
    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final Predicate<Address> systemAccountDetector;

    public HederaExtCodeSizeOperationV045(
            GasCalculator gasCalculator,
            BiPredicate<Address, MessageFrame> addressValidator,
            Predicate<Address> systemAccountDetector) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
        this.systemAccountDetector = systemAccountDetector;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        final Supplier<OperationResult> systemAccountExecutionSupplier = () -> {
            frame.popStackItems(1);
            frame.pushStackItem(UInt256.ZERO);
            return new OperationResult(cost(true), null);
        };
        return HederaEvmOperationsUtilV045.addressCheckExecution(
                frame,
                () -> frame.getStackItem(0),
                () -> cost(true),
                () -> super.execute(frame, evm),
                addressValidator,
                systemAccountDetector,
                systemAccountExecutionSupplier);
    }
}

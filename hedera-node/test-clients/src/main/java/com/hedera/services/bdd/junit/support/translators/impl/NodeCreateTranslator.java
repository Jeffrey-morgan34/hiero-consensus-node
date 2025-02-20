// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support.translators.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.hapi.utils.EntityType.NODE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This translator extracts a node ID from NodeCreate, NodeUpdate, and NodeDelete transactions.
 */
public class NodeCreateTranslator implements BlockTransactionPartsTranslator {
    private static final Logger log = LogManager.getLogger(NodeCreateTranslator.class);

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> {
            if (parts.status() == SUCCESS) {
                final var iter = remainingStateChanges.listIterator();
                final long createdNodeId = baseTranslator.nextCreatedNum(NODE);
                while (iter.hasNext()) {
                    final var stateChange = iter.next();
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().valueOrThrow().hasNodeValue()) {
                        final long nodeId =
                                stateChange.mapUpdateOrThrow().keyOrThrow().entityNumberKeyOrThrow();
                        if (nodeId == createdNodeId) {
                            receiptBuilder.nodeId(nodeId);
                            iter.remove();
                            return;
                        }
                    }
                }
                log.error(
                        "No matching state change found for successful node create with id {}",
                        parts.transactionIdOrThrow());
            }
        });
    }
}

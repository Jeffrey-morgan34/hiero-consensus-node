package com.hedera.node.app.service.contract.impl.state;

import com.hedera.hapi.node.base.LambdaID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.lambda.LambdaState;
import com.hedera.node.app.service.contract.impl.state.StorageAccess.StorageAccessType;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_KEY;
import static com.hedera.node.app.service.contract.impl.schemas.V061ContractSchema.LAMBDA_STATES_KEY;

/**
 * Read/write access to lambda states.
 */
public class WritableLambdaStore extends ReadableLambdaStore {
    private final WritableKVState<SlotKey, SlotValue> storage;
    private final WritableKVState<LambdaID, LambdaState> lambdaStates;

    public WritableLambdaStore(@NonNull final WritableStates states) {
        super(states);
        this.storage = states.get(STORAGE_KEY);
        this.lambdaStates = states.get(LAMBDA_STATES_KEY);
    }

    /**
     * Puts the given slot values for the given lambda, ensuring storage linked list pointers are preserved.
     * If a new value is {@link Bytes#EMPTY}, the slot is removed.
     * @param lambdaId the lambda ID
     * @param keys the keys
     * @param newValues the new values
     * @throws IllegalArgumentException if the lambda ID is not found
     */
    public void updateSlots(
            @NonNull final LambdaID lambdaId,
            @NonNull final List<Bytes> keys,
            @NonNull final List<Bytes> newValues) {
        final var view = getView(lambdaId, keys);

    }

    private record SlotUpdate(@NonNull Bytes key, @Nullable Bytes oldValue, @Nullable Bytes newValue) {
        public StorageAccessType asAccessType() {
            if (oldValue == null) {

            }
            throw new AssertionError("Not implemented");
        }
    }
}

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

package com.hedera.node.app.blocks.impl;

import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapDeleteChange;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.state.StateChangesListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class KVStateChangeListener implements StateChangesListener {
    private static final Set<DataType> TARGET_DATA_TYPES = EnumSet.of(DataType.MAP);

    private List<StateChange> stateChanges = new ArrayList<>();

    @Override
    public Set<DataType> targetDataTypes() {
        return TARGET_DATA_TYPES;
    }

    @Override
    public <K, V> void mapUpdateChange(@NonNull final String stateName, @NonNull final K key, @NonNull final V value) {
        Objects.requireNonNull(stateName, "stateName must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        final var change = MapUpdateChange.newBuilder()
                .key(mapChangeKeyFor(key))
                .value(mapChangeValueFor(value))
                .build();
        final var stateChange =
                StateChange.newBuilder().stateName(stateName).mapUpdate(change).build();
        stateChanges.add(stateChange);
    }

    @Override
    public <K> void mapDeleteChange(@NonNull final String stateName, @NonNull final K key) {
        Objects.requireNonNull(stateName, "stateName must not be null");
        Objects.requireNonNull(key, "key must not be null");

        final var change =
                MapDeleteChange.newBuilder().key(mapChangeKeyFor(key)).build();
        stateChanges.add(
                StateChange.newBuilder().stateName(stateName).mapDelete(change).build());
    }

    private static <K> MapChangeKey mapChangeKeyFor(@NonNull final K key) {
        return switch (key) {
            case AccountID accountID -> MapChangeKey.newBuilder()
                    .accountIdKey(accountID)
                    .build();
            case EntityIDPair entityIDPair -> MapChangeKey.newBuilder()
                    .entityIdPairKey(entityIDPair)
                    .build();
            case EntityNumber entityNumber -> MapChangeKey.newBuilder()
                    .entityNumberKey(entityNumber)
                    .build();
            case FileID fileID -> MapChangeKey.newBuilder().fileIdKey(fileID).build();
            case NftID nftID -> MapChangeKey.newBuilder().nftIdKey(nftID).build();
            case ProtoBytes protoBytes -> MapChangeKey.newBuilder()
                    .protoBytesKey(protoBytes.value())
                    .build();
            case ProtoLong protoLong -> MapChangeKey.newBuilder()
                    .protoLongKey(protoLong.value())
                    .build();
            case ProtoString protoString -> MapChangeKey.newBuilder()
                    .protoStringKey(protoString.value())
                    .build();
            case ScheduleID scheduleID -> MapChangeKey.newBuilder()
                    .scheduleIdKey(scheduleID)
                    .build();
            case SlotKey slotKey -> MapChangeKey.newBuilder()
                    .slotKeyKey(slotKey)
                    .build();
            case TokenID tokenID -> MapChangeKey.newBuilder()
                    .tokenIdKey(tokenID)
                    .build();
            case TopicID topicID -> MapChangeKey.newBuilder()
                    .topicIdKey(topicID)
                    .build();
            case ContractID contractID -> MapChangeKey.newBuilder()
                    .contractIdKey(contractID)
                    .build();
            default -> throw new IllegalStateException(
                    "Unrecognized key type " + key.getClass().getSimpleName());
        };
    }

    private static <V> MapChangeValue mapChangeValueFor(@NonNull final V value) {
        return switch (value) {
            case Account account -> MapChangeValue.newBuilder()
                    .accountValue(account)
                    .build();
            case AccountID accountID -> MapChangeValue.newBuilder()
                    .accountIdValue(accountID)
                    .build();
            case Bytecode bytecode -> MapChangeValue.newBuilder()
                    .bytecodeValue(bytecode)
                    .build();
            case File file -> MapChangeValue.newBuilder().fileValue(file).build();
            case Nft nft -> MapChangeValue.newBuilder().nftValue(nft).build();
            case ProtoString protoString -> MapChangeValue.newBuilder()
                    .protoStringValue(protoString.value())
                    .build();
            case Schedule schedule -> MapChangeValue.newBuilder()
                    .scheduleValue(schedule)
                    .build();
            case ScheduleList scheduleList -> MapChangeValue.newBuilder()
                    .scheduleListValue(scheduleList)
                    .build();
            case SlotValue slotValue -> MapChangeValue.newBuilder()
                    .slotValueValue(slotValue)
                    .build();
            case StakingNodeInfo stakingNodeInfo -> MapChangeValue.newBuilder()
                    .stakingNodeInfoValue(stakingNodeInfo)
                    .build();
            case Token token -> MapChangeValue.newBuilder().tokenValue(token).build();
            case TokenRelation tokenRelation -> MapChangeValue.newBuilder()
                    .tokenRelationValue(tokenRelation)
                    .build();
            case Topic topic -> MapChangeValue.newBuilder().topicValue(topic).build();
            default -> throw new IllegalStateException(
                    "Unexpected value: " + value.getClass().getSimpleName());
        };
    }

    public List<StateChange> getStateChanges() {
        return stateChanges;
    }
}

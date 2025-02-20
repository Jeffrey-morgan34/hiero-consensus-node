// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.ids;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.EntityIdFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

public class AppEntityIdFactory implements EntityIdFactory {
    private final long shard;
    private final long realm;

    public AppEntityIdFactory(@NonNull final Configuration bootstrapConfig) {
        requireNonNull(bootstrapConfig);
        final var hederaConfig = bootstrapConfig.getConfigData(HederaConfig.class);
        this.shard = hederaConfig.shard();
        this.realm = hederaConfig.realm();
    }

    @Override
    public TokenID newTokenId(final long number) {
        return new TokenID(shard, realm, number);
    }

    @Override
    public TopicID newTopicId(final long number) {
        return new TopicID(shard, realm, number);
    }

    @Override
    public ScheduleID newScheduleId(final long number) {
        return new ScheduleID(shard, realm, number);
    }

    @Override
    public AccountID newAccountId(long number) {
        return AccountID.newBuilder()
                .shardNum(shard)
                .realmNum(realm)
                .accountNum(number)
                .build();
    }

    @Override
    public AccountID newAccountIdWithAlias(@NonNull Bytes alias) {
        return AccountID.newBuilder()
                .shardNum(shard)
                .realmNum(realm)
                .alias(alias)
                .build();
    }

    @Override
    public AccountID newDefaultAccountId() {
        return AccountID.newBuilder().shardNum(shard).realmNum(realm).build();
    }

    @Override
    public FileID newFileId(long number) {
        return new FileID(shard, realm, number);
    }

    @Override
    public ContractID newContractId(long number) {
        return ContractID.newBuilder()
                .shardNum(shard)
                .realmNum(realm)
                .contractNum(number)
                .build();
    }
}

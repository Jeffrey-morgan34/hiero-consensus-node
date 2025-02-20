// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.hedera.remote;

import static com.hedera.services.bdd.junit.hedera.NodeMetadata.UNKNOWN_PORT;
import static com.hedera.services.bdd.spec.TargetNetworkType.REMOTE_NETWORK;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.services.bdd.junit.hedera.AbstractGrpcNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.spec.TargetNetworkType;
import com.hedera.services.bdd.spec.infrastructure.HapiClients;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A network of Hedera nodes already running on remote processes and accessed via gRPC.
 */
public class RemoteNetwork extends AbstractGrpcNetwork implements HederaNetwork {
    private static final String REMOTE_NETWORK_NAME = "JRS_SCOPE";

    private RemoteNetwork(
            @NonNull final String networkName,
            @NonNull final List<HederaNode> nodes,
            @NonNull final HapiClients clients) {
        super(networkName, nodes, clients);
    }

    /**
     * Create a new network of remote nodes.
     *
     * @param nodeConnectInfos the connection information for the remote nodes
     * @return the new network
     */
    public static HederaNetwork newRemoteNetwork(
            @NonNull final List<NodeConnectInfo> nodeConnectInfos, @NonNull final HapiClients clients) {
        requireNonNull(nodeConnectInfos);
        requireNonNull(clients);
        return new RemoteNetwork(
                REMOTE_NETWORK_NAME,
                IntStream.range(0, nodeConnectInfos.size())
                        .<HederaNode>mapToObj(
                                nodeId -> new RemoteNode(metadataFor(nodeId, nodeConnectInfos.get(nodeId))))
                        .toList(),
                clients);
    }

    @Override
    public TargetNetworkType type() {
        return REMOTE_NETWORK;
    }

    @Override
    public void start() {
        // No-op, a remote network must already be started
    }

    @Override
    public void terminate() {
        throw new UnsupportedOperationException("Cannot terminate a remote network");
    }

    @Override
    public void awaitReady(@NonNull Duration timeout) {
        // No-op, a remote network must already be ready
    }

    private static NodeMetadata metadataFor(final int nodeId, @NonNull final NodeConnectInfo connectInfo) {
        return new NodeMetadata(
                nodeId,
                "node" + nodeId,
                AccountID.newBuilder()
                        .shardNum(connectInfo.getAccount().getShardNum())
                        .realmNum(connectInfo.getAccount().getRealmNum())
                        .accountNum(connectInfo.getAccount().getAccountNum())
                        .build(),
                connectInfo.getHost(),
                connectInfo.getPort(),
                UNKNOWN_PORT,
                UNKNOWN_PORT,
                UNKNOWN_PORT,
                UNKNOWN_PORT,
                null);
    }
}

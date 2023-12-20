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

package com.hedera.services.bdd.junit;

import edu.umd.cs.findbugs.annotations.NonNull;

public class VlanNetworkHapiTestEnv extends HapiTestEnvBase {

    private static final IPAllocator VLAN_IP_ALLOC = nodeId -> String.format("10.191.%d.1", nodeId);

    private static final PortAllocator VLAN_GRPC_PORT_ALLOC = nodeId -> FIRST_GRPC_PORT + (nodeId * 2);

    private static final PortAllocator VLAN_GOSSIP_PORT_ALLOC = nodeId -> 50111;

    public VlanNetworkHapiTestEnv(
            @NonNull final String testName, final boolean cluster, final boolean useInProcessAlice) {
        super(VLAN_IP_ALLOC, VLAN_GOSSIP_PORT_ALLOC, VLAN_GRPC_PORT_ALLOC);
        initialize(testName, cluster, useInProcessAlice);
    }

    @Override
    protected void setupNetwork(
            final int nodeId, @NonNull final String nodeAddress, final int gossipPort, final int grpcPort) {
        createVlanAdapter(nodeId);
        assignNetworkAddress(nodeId, nodeAddress);
    }

    @Override
    protected void teardownNetwork(final int nodeId, @NonNull final String nodeAddress) {
        destroyVlanAdapter(nodeId);
    }

    private void createVlanAdapter(final int nodeId) {
        manipulateVlanAdapter(nodeId, "create");
    }

    private void destroyVlanAdapter(final int nodeId) {
        manipulateVlanAdapter(nodeId, "destroy");
    }

    private void manipulateVlanAdapter(final int nodeId, @NonNull final String op) {
        executeWithElevation("ifconfig", String.format("vlan10%d", nodeId), op);
    }

    private void assignNetworkAddress(final int nodeId, @NonNull String nodeAddress) {
        executeWithElevation(
                "ifconfig", String.format("vlan10%d", nodeId), "inet", nodeAddress, "netmask", "255.255.255.0");
    }
}

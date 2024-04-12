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

package com.swirlds.platform.network.connectivity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.utility.Pair;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.crypto.CryptoArgsProvider;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TlsFactoryTest extends ConnectivityTestBase {
    private static final int PORT = 34_000;

    private static SocketFactory socketFactoryA;
    private static SocketFactory socketFactoryC;
    private static Socket clientSocketB;
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static AddressBook updatedAddressBook;
    private final AtomicBoolean closeSeverConnection = new AtomicBoolean(false);
    private static NodeId nodeA;

    @BeforeEach
    void setUp() throws Throwable {
        // create addressBook, keysAndCerts
        final Pair<AddressBook, Map<NodeId, KeysAndCerts>> akPair1 = CryptoArgsProvider.getAddressBookWithKeys(2);
        final AddressBook addressBook = akPair1.left();
        final Map<NodeId, KeysAndCerts> keysAndCerts = akPair1.right();
        assertTrue(addressBook.getSize() > 1, "Address book must contain at least 2 nodes");

        // choose 2 nodes to test connections
        nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(1);

        // create their socket factories
        socketFactoryA =
                NetworkUtils.createSocketFactory(nodeA, addressBook, keysAndCerts.get(nodeA), TLS_NO_IP_TOS_CONFIG);
        final SocketFactory socketFactoryB =
                NetworkUtils.createSocketFactory(nodeB, addressBook, keysAndCerts.get(nodeB), TLS_NO_IP_TOS_CONFIG);

        // test that B can talk to A - A(serverSocket) -> B(clientSocket1)
        serverSocket = socketFactoryA.createServerSocket(PORT);
        serverSocket.setSoTimeout(10000);
        serverThread = createSocketThread(serverSocket, closeSeverConnection);

        clientSocketB = socketFactoryB.createClientSocket(STRING_IP, PORT);
        testSocket(serverThread, clientSocketB);
        Assertions.assertFalse(serverSocket.isClosed());

        // create a new address book with keys and new set of nodes
        final Pair<AddressBook, Map<NodeId, KeysAndCerts>> addressBookWithKeys =
                CryptoArgsProvider.getAddressBookWithKeys(6);
        updatedAddressBook = addressBookWithKeys.left();
        final Address address = addressBook.getAddress(nodeA).copySetNodeId(updatedAddressBook.getNextNodeId());
        updatedAddressBook.add(address); // ensure original node is in new
        final Map<NodeId, KeysAndCerts> updatedKeysAndCerts = addressBookWithKeys.right();
        assertTrue(updatedAddressBook.getSize() > 1, "Address book must contain at least 2 nodes");

        // pick a node for the 3rd connection C.
        final NodeId node3 = updatedAddressBook.getNodeId(4);
        final KeysAndCerts keysAndCerts3 = updatedKeysAndCerts.get(node3);
        socketFactoryC =
                NetworkUtils.createSocketFactory(node3, updatedAddressBook, keysAndCerts3, TLS_NO_IP_TOS_CONFIG);
    }

    /**
     * Asserts that for sockets A and B that can connect to each other, if A's peer list changes and in effect its trust
     * store is reloaded, B, as well as peer C in the updated peer list can still connect to A, and A to them.
     */
    @Test
    void tlsFactoryRefreshTest() throws Throwable {
        // re-initialize SSLContext for A using a new peer list which contains C
        socketFactoryA.refresh(Utilities.createPeerInfoList(updatedAddressBook, nodeA));
        // now, we expect that C can talk to A
        final Socket clientSocketC = socketFactoryC.createClientSocket(STRING_IP, PORT);
        testSocket(serverThread, clientSocketC);
        // also, B can still talk to A
        testSocket(serverThread, clientSocketB);
        closeSeverConnection.set(true);
        serverThread.join();
        Assertions.assertTrue(serverSocket.isClosed());
    }

    /**
     * Asserts that for sockets A and B that can connect to each other, a third socket C is unable to connect.
     */
    @Test
    void tlsFactoryUntrustedConnectionThrowsTest() throws Throwable {
        // we expect that C can't talk to A yet, as C's certificate is not in A's trust store
        assertThrows(IOException.class, () -> socketFactoryC.createClientSocket(STRING_IP, PORT));
        closeSeverConnection.set(true);
        serverThread.join();
        Assertions.assertTrue(serverSocket.isClosed());
    }
}

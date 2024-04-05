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

package com.swirlds.platform.network;

import static com.swirlds.platform.crypto.CryptoStatic.loadKeys;

import com.swirlds.common.crypto.internal.CryptoUtils;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.io.ResourceLoader;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeyCertPurpose;
import com.swirlds.platform.crypto.KeyGeneratingException;
import com.swirlds.platform.crypto.KeyLoadingException;
import com.swirlds.platform.crypto.PublicStores;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetworkPeerIdentifierTest {
    /**
     * Tests that given a list of valid Swirlds production certificates (like the type used in mainnet),
     * {@link NetworkPeerIdentifier#identifyTlsPeer(Certificate[], List)} is able to successfully identify a matching
     * peer.
     */
    @Test
    void testExtractPeerInfoWorksForMainnet()
            throws URISyntaxException, KeyLoadingException, KeyStoreException, InvalidAlgorithmParameterException {

        // sample node names from mainnet
        final List<String> names =
                List.of("node1", "node2", "node3", "node4", "node5", "node6", "node7", "node8", "node9", "node10");
        // sample pfx file grabbed from mainnet
        final KeyStore publicKeys = loadKeys(
                ResourceLoader.getFile("preGeneratedKeysAndCerts/").resolve("publicMainnet.pfx"),
                "password".toCharArray());
        final PublicStores publicStores = PublicStores.fromAllPublic(publicKeys, names);

        final List<PeerInfo> peerInfoList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final String name = names.get(i);
            final NodeId node = new NodeId(i);
            final PeerInfo peer = new PeerInfo(
                    node,
                    names.get(i),
                    "127.0.0.1",
                    Objects.requireNonNull(publicStores.getCertificate(KeyCertPurpose.SIGNING, name)));
            peerInfoList.add(peer);
        }

        final PKIXParameters params = new PKIXParameters(publicStores.agrTrustStore());
        final Set<TrustAnchor> trustAnchors = params.getTrustAnchors();

        final Certificate[] certificates =
                trustAnchors.stream().map(TrustAnchor::getTrustedCert).toArray(Certificate[]::new);
        final PeerInfo matchedPeer = new NetworkPeerIdentifier().identifyTlsPeer(certificates, peerInfoList);
        Assertions.assertNotNull(matchedPeer);
    }

    @Test
    @DisplayName(
            "asserts that when none of the peer's certificate matches any of the certs in the trust store, identifyTlsPeer returns null")
    void testIdentifyTlsPeerReturnsNull()
            throws URISyntaxException, KeyLoadingException, KeyStoreException, NoSuchAlgorithmException,
            NoSuchProviderException, KeyGeneratingException {
        final List<String> names =
                List.of("node1", "node2", "node3", "node4", "node5", "node6", "node7", "node8", "node9", "node10");
        // sample pfx file grabbed from mainnet
        final KeyStore publicKeys = loadKeys(
                ResourceLoader.getFile("preGeneratedKeysAndCerts/").resolve("publicMainnet.pfx"),
                "password".toCharArray());
        final PublicStores publicStores = PublicStores.fromAllPublic(publicKeys, names);

        // create list of peers with valid certs
        final List<PeerInfo> peerInfoList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final String name = names.get(i);
            final NodeId node = new NodeId(i);
            final PeerInfo peer = new PeerInfo(
                    node,
                    names.get(i),
                    "127.0.0.1",
                    Objects.requireNonNull(publicStores.getCertificate(KeyCertPurpose.SIGNING, name)));
            peerInfoList.add(peer);
        }

        final SecureRandom secureRandom = CryptoUtils.getDetRandom();

        final KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
        rsaKeyGen.initialize(3072, secureRandom);
        final KeyPair rsaKeyPair1 = rsaKeyGen.generateKeyPair();
        final KeyPair rsaKeyPair2 = rsaKeyGen.generateKeyPair();

        final String name = "CN=Bob";
        final X509Certificate rsaCert =
                CryptoStatic.generateCertificate(name, rsaKeyPair1, name, rsaKeyPair1, secureRandom);
        final X509Certificate ecCert =
                CryptoStatic.generateCertificate(name, rsaKeyPair2, name, rsaKeyPair2, secureRandom);
        final Certificate[] certificates = new Certificate[]{rsaCert, ecCert};

        final PeerInfo matchedPeer = new NetworkPeerIdentifier().identifyTlsPeer(certificates, peerInfoList);
        Assertions.assertNull(matchedPeer);
    }
}

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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.SOCKET_EXCEPTIONS;

import com.swirlds.base.time.Time;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Identifies a connected peer from a list of trusted peers
 */
public class NetworkPeerIdentifier {

    private static final Logger logger = LogManager.getLogger(NetworkPeerIdentifier.class);

    /**
     * limits the frequency of error log statements
     */
    private static final RateLimitedLogger noPeerFoundLogger =
            new RateLimitedLogger(logger, Time.getCurrent(), Duration.ofMinutes(5));

    private static final RateLimitedLogger multiplePeersFoundLogger =
            new RateLimitedLogger(logger, Time.getCurrent(), Duration.ofMinutes(5));

    /**
     * identifies a client on the other end of the socket using their signing certificate.
     *
     * @param certs        a list of TLS certificates (e.g. the certificates in a trust store)
     * @param peerInfoList List of known peers
     * @return info of the identified peer
     */
    public @Nullable PeerInfo identifyTlsPeer(
            final @NonNull Certificate[] certs, final @NonNull List<PeerInfo> peerInfoList) {
        PeerInfo matchingPair = null;
        final X509Certificate agreementCert = (X509Certificate) certs[0];
        final Set<PeerInfo> peers = peerInfoList.stream()
                .filter(peerInfo -> ((X509Certificate) peerInfo.signingCertificate())
                        .getSubjectX500Principal()
                        .equals(agreementCert.getIssuerX500Principal()))
                .collect(Collectors.toSet());
        final Optional<PeerInfo> peer = peers.stream().findFirst();
        if (peer.isEmpty()) {
            noPeerFoundLogger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Unable to find a matching peer with the presented certificate {}.",
                    agreementCert);
        } else {
            if (peers.size() > 1) {
                multiplePeersFoundLogger.info(
                        EXCEPTION.getMarker(), "Found {} matching peers for the presented certificate", peers.size());
            }
            matchingPair = peer.get();
        }
        return matchingPair;
    }
}

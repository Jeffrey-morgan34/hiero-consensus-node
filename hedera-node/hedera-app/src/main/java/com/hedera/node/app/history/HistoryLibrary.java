// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.history;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * The cryptographic operations required by the {@link HistoryService}.
 */
public interface HistoryLibrary {
    /**
     * Returns the SNARK verification key in use by this library.
     * <p>
     * <b>Important:</b> If this changes, the ledger id must also change.
     */
    byte[] snarkVerificationKey();

    /**
     * Returns a new Schnorr key pair.
     */
    byte[] newSchnorrKeyPair();

    /**
     * Signs a message with a Schnorr private key. In Hiero TSS, this will always be the concatenation
     * of an address book hash and the associated metadata.
     *
     * @param message the message
     * @param privateKey the private key
     * @return the signature
     */
    byte[] signSchnorr(@NonNull byte[] message, @NonNull byte[] privateKey);

    /**
     * Checks that a signature on a message verifies under a Schnorr public key.
     *
     * @param signature the signature
     * @param message the message
     * @param publicKey the public key
     * @return true if the signature is valid; false otherwise
     */
    boolean verifySchnorr(@NonNull byte[] signature, @NonNull byte[] message, @NonNull byte[] publicKey);

    /**
     * Computes the hash of the given address book with the same algorithm used by the SNARK circuit.
     *
     * @param addressBook the address book
     * @return the hash of the address book
     */
    byte[] hashAddressBook(@NonNull byte[] addressBook);

    /**
     * Returns a SNARK recursively proving the target address book and associated metadata belong to the given ledger
     * id's chain of trust that includes the given source address book, based on its own proof of belonging. (Unless the
     * source address book hash <i>is</i> the ledger id, which is the base case of the recursion).
     *
     * @param ledgerId the ledger id, the concatenation of the genesis address book hash and the SNARK verification key
     * @param sourceProof if not null, the proof the source address book is in the ledger id's chain of trust
     * @param sourceAddressBook the source roster
     * @param sourceSignatures the source address book signatures on the target address book hash and its metadata
     * @param targetAddressBookHash the hash of the target address book
     * @param targetMetadata the metadata of the target address book
     * @return the SNARK proving the target address book and metadata belong to the ledger id's chain of trust
     */
    @NonNull
    byte[] proveChainOfTrust(
            @NonNull byte[] ledgerId,
            @Nullable byte[] sourceProof,
            @NonNull byte[] sourceAddressBook,
            @NonNull Map<Long, byte[]> sourceSignatures,
            @NonNull byte[] targetAddressBookHash,
            @NonNull byte[] targetMetadata);

    /**
     * Verifies the given SNARK proves the given address book hash and associated metadata belong to the given
     * ledger id's chain of trust
     * @param ledgerId the ledger id
     * @param addressBookHash the hash of the address book
     * @param metadata the metadata associated to the address book
     * @param proof the SNARK proving the address book hash and metadata belong to the ledger id's chain of trust
     * @return true if the proof is valid; false otherwise
     */
    boolean verifyChainOfTrust(
            @NonNull byte[] ledgerId, @NonNull byte[] addressBookHash, @NonNull byte[] metadata, @NonNull byte[] proof);
}

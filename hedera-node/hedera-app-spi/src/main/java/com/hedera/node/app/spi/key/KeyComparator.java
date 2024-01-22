/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.key;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Key.KeyOneOfType;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Comparator;
import java.util.List;

/**
 * Comparator used to impose a deterministic ordering on collections of Key objects.
 * <br>These include maps, sets, lists, arrays, etc...
 * <br>The methods in this class are used in hot spot code, so allocation must be kept to a bare
 * minimum, and anything likely to have performance questions should be avoided.
 * <br>Note that comparing keys is unavoidably costly.  We try to exit as early as possible throughout
 * this class, but worst case we're comparing every simple key byte-by-byte for the entire tree, which
 * may be up to 15 levels deep with any number of keys per level.  We haven't seen a key with
 * several million "simple" keys included, but that does not mean nobody will create one.
 */
public class KeyComparator implements Comparator<Key> {
    @Override
    public int compare(final Key first, final Key second) {
        // use a temporary so we can "normalize" results at the end.
        int result;
        if (first == second) result = 0;
        else if (first == null) result = -1;
        else if (second == null) result = 1;
        // Note, record defines equals, but it uses reference equality for reference type members.
        //       We must not use reference equality here, so we cannot use that.
        else if (first.key() == null) result = second.key() == null ? 0 : -1;
        else if (second.key() == null) result = 1;
        else {
            final KeyOneOfType firstKeyType = first.key().kind();
            final KeyOneOfType secondKeyType = second.key().kind();
            if (firstKeyType != secondKeyType)
                result = firstKeyType.compareTo(secondKeyType);
            else {
                // both keys are the same type; so compare the details.
                result = switch (firstKeyType) {
                    case UNSET -> 0; // both unset compares equal.
                    case CONTRACT_ID -> compareContractId(first, second);
                    case DELEGATABLE_CONTRACT_ID -> compareDelegateable(first, second);
                    case ED25519 -> compareEdwards(first, second);
                    case ECDSA_SECP256K1 -> compareSecp256k(first, second);
                    case THRESHOLD_KEY -> compareThreshold(first, second);
                    case KEY_LIST -> compareKeyList(first, second);
                    // The next two are not currently supported key types.
                    case RSA_3072 -> compareRsa(first, second);
                    case ECDSA_384 -> compareEcdsa(first, second);
                };
            }
        }
        // Use Integer.compare to "normalize" result as exactly -1, 0, or 1
        return Integer.compare(result, 0);
    }

    private int compareContractId(final Key first, final Key second) {
        final ContractID lhs = first.contractID();
        final ContractID rhs = second.contractID();
        if (lhs == rhs) return 0;
        else if (lhs == null) return -1;
        else if (rhs == null) return 1;
        else return lhs.compareTo(rhs);
    }

    private int compareDelegateable(final Key first, final Key second) {
        final ContractID lhs = first.delegatableContractId();
        final ContractID rhs = second.delegatableContractId();
        if (lhs == rhs) return 0;
        else if (lhs == null) return -1;
        else if (rhs == null) return 1;
        else return lhs.compareTo(rhs);
    }

    private int compareEdwards(final Key first, final Key second) {
        final Bytes lhs = first.ed25519();
        final Bytes rhs = second.ed25519();
        return compareBytes(lhs, rhs);
    }

    private int compareSecp256k(final Key first, final Key second) {
        final Bytes lhs = first.ecdsaSecp256k1();
        final Bytes rhs = second.ecdsaSecp256k1();
        return compareBytes(lhs, rhs);
    }

    private int compareThreshold(final Key first, final Key second) {
        final ThresholdKey lhs = first.thresholdKey();
        final ThresholdKey rhs = second.thresholdKey();
        if (lhs == rhs) return 0;
        else if (lhs == null) return -1;
        else if (rhs == null) return 1;

        final int leftThreshold = lhs.threshold();
        final int rightThreshold = rhs.threshold();
        if (leftThreshold != rightThreshold) return leftThreshold > rightThreshold ? 1 : -1;

        final KeyList leftList = lhs.keys();
        final KeyList rightList = rhs.keys();
        if (leftList == rightList) return 0;
        else if (leftList == null) return -1;
        else if (rightList == null) return 1;
        else return compareListOfKeys(leftList.keys(), rightList.keys());
    }

    private int compareKeyList(final Key first, final Key second) {
        final KeyList lhs = first.keyList();
        final KeyList rhs = second.keyList();
        if (lhs == rhs) return 0;
        else if (lhs == null) return -1;
        else if (rhs == null) return 1;
        return compareListOfKeys(lhs.keys(), rhs.keys());
    }

    private int compareEcdsa(final Key first, final Key second) {
        throw new UnsupportedOperationException("Key Type ECDSA 384 is not supported");
    }

    private int compareRsa(final Key first, final Key second) {
        throw new UnsupportedOperationException("Key Type RSA 3072 is not supported");
    }

    // IMPORTANT NOTE: This method relies on the order of each List to be consistent across all
    //     nodes in the network, and *List Order Is Significant*.  This is currently true, but
    //     if it ever changes, then this method must change to perform an exhaustive comparison
    //     (nested loops) of the two lists (making order within the lists not need to match).
    private int compareListOfKeys(final List<Key> lhs, final List<Key> rhs) {
        if (lhs == rhs) return 0;
        else if (lhs == null) return -1;
        else if (rhs == null) return 1;

        final int leftLength = lhs.size();
        final int rightLength = rhs.size();
        if (leftLength != rightLength) return leftLength - rightLength;
        else
            for (int i = 0; i < leftLength; i++) {
                final int comparison = compare(lhs.get(i), rhs.get(i));
                if (comparison != 0) return comparison;
            }
        // nothing differed between the two lists; they are equal.
        return 0;
    }

    private int compareBytes(final Bytes lhs, final Bytes rhs) {
        if (lhs == rhs) return 0;
        else if (lhs == null) return -1;
        else if (rhs == null) return 1;
        else return lhs.compareTo(rhs);
    }
}

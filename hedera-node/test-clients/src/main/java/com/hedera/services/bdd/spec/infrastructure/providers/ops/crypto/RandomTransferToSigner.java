// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto;

import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;

public class RandomTransferToSigner extends RandomTransfer {
    private final ResponseCodeEnum[] outcomes;
    private final String signer;

    public RandomTransferToSigner(
            RegistrySourcedNameProvider<AccountID> accounts, String signer, ResponseCodeEnum[] outcomes) {
        super(accounts, outcomes);
        this.signer = signer;
        this.outcomes = outcomes;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var from = accounts.getQualifying();
        if (from.isEmpty()) {
            return Optional.empty();
        }

        HapiCryptoTransfer op = cryptoTransfer(tinyBarsFromTo(from.get(), signer, 1L))
                .signedBy(signer)
                .payingWith(signer)
                .sigMapPrefixes(uniqueWithFullPrefixesFor(signer))
                .hasPrecheckFrom(standardPrechecksAnd(PAYER_ACCOUNT_NOT_FOUND, ACCOUNT_DELETED, PAYER_ACCOUNT_DELETED))
                .hasKnownStatusFrom(outcomes)
                .noLogging();

        return Optional.of(op);
    }
}

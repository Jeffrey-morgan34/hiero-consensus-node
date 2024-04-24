/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.token;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.bdd.spec.PropertySource.asAccountString;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asIdForKeyLookUp;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenRevokeKycUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.crypto.ReferenceType;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenKycRevoke extends HapiTxnOp<HapiTokenKycRevoke> {
    static final Logger log = LogManager.getLogger(HapiTokenKycRevoke.class);

    private final String token;
    private String account;
    private String alias = null;
    private ReferenceType referenceType = ReferenceType.REGISTRY_NAME;

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenRevokeKycFromAccount;
    }

    public HapiTokenKycRevoke(final String token, final String account) {
        this(token, account, ReferenceType.REGISTRY_NAME);
    }

    public HapiTokenKycRevoke(final String token, final String reference, ReferenceType referenceType) {
        this.token = token;
        this.referenceType = referenceType;
        if (referenceType == ReferenceType.ALIAS_KEY_NAME) {
            this.alias = reference;
        } else {
            this.account = reference;
        }
    }

    @Override
    protected HapiTokenKycRevoke self() {
        return this;
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.TokenRevokeKycFromAccount, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        return TokenRevokeKycUsage.newEstimate(txn, new TxnUsageEstimator(suFrom(svo), txn, ESTIMATOR_UTILS))
                .get();
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        AccountID aId;
        if (referenceType == ReferenceType.REGISTRY_NAME) {
            aId = TxnUtils.asId(account, spec);
        } else {
            aId = asIdForKeyLookUp(alias, spec);
            account = asAccountString(aId);
        }
        final var tId = TxnUtils.asTokenId(token, spec);
        final TokenRevokeKycTransactionBody opBody = spec.txns()
                .<TokenRevokeKycTransactionBody, TokenRevokeKycTransactionBody.Builder>body(
                        TokenRevokeKycTransactionBody.class, b -> {
                            b.setAccount(aId);
                            b.setToken(tId);
                        });
        return b -> b.setTokenRevokeKyc(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return List.of(spec -> spec.registry().getKey(effectivePayer(spec)), spec -> spec.registry()
                .getKycKey(token));
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(final HapiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::revokeKycFromTokenAccount;
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) {}

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper()
                .add("token", token)
                .add("account", account)
                .add("alias", alias);
        return helper;
    }
}

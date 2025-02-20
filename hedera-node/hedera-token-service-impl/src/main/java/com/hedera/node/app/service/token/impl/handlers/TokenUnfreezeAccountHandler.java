// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.node.app.spi.workflows.WorkflowException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.WorkflowException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_UNFREEZE_ACCOUNT}.
 */
@Singleton
public class TokenUnfreezeAccountHandler implements TransactionHandler {
    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenUnfreezeAccountHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) {
        requireNonNull(context);
        final var op = context.body().tokenUnfreezeOrThrow();

        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) throw new WorkflowException(INVALID_TOKEN_ID);
        if (tokenMeta.hasFreezeKey()) {
            context.requireKey(tokenMeta.freezeKey());
        } else {
            throw new WorkflowException(TOKEN_HAS_NO_FREEZE_KEY);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws WorkflowException {
        requireNonNull(context);

        final var op = context.body().tokenUnfreezeOrThrow();
        final var storeFactory = context.storeFactory();
        final var accountStore = storeFactory.readableStore(ReadableAccountStore.class);
        final var tokenStore = storeFactory.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = storeFactory.writableStore(WritableTokenRelationStore.class);
        final var expiryValidator = context.expiryValidator();
        final var tokenRel = validateSemantics(op, accountStore, tokenStore, tokenRelStore, expiryValidator);

        final var copyBuilder = tokenRel.copyBuilder();
        copyBuilder.frozen(false);
        tokenRelStore.put(copyBuilder.build());
    }

    /**
     * Performs checks independent of state or context.
     */
    @Override
    public void pureChecks(@NonNull final PureChecksContext context) {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenUnfreezeOrThrow();
        if (!op.hasToken()) {
            throw new WorkflowException(INVALID_TOKEN_ID);
        }

        if (!op.hasAccount()) {
            throw new WorkflowException(INVALID_ACCOUNT_ID);
        }
    }

    /**
     * Performs checks that the given token and accounts from the state are valid and that the
     * token is associated to the account.
     *
     * @return the token relation for the given token and account
     */
    private TokenRelation validateSemantics(
            @NonNull final TokenUnfreezeAccountTransactionBody op,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final ExpiryValidator expiryValidator)
            throws WorkflowException {
        // Check that the token meta exists
        final var tokenId = op.tokenOrElse(TokenID.DEFAULT);

        // Validate token is not paused or deleted
        TokenHandlerHelper.getIfUsable(tokenId, tokenStore);

        final var tokenMeta = tokenStore.getTokenMeta(tokenId);
        validateTrue(tokenMeta != null, INVALID_TOKEN_ID);

        // Check that the token has a freeze key
        validateTrue(tokenMeta.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);

        // Check that the account exists
        final var accountId = op.accountOrElse(AccountID.DEFAULT);
        final var account =
                TokenHandlerHelper.getIfUsable(accountId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);

        // Check that token exists
        TokenHandlerHelper.getIfUsable(tokenId, tokenStore);

        // Check that the token is associated to the account
        final var tokenRel = tokenRelStore.get(accountId, tokenId);
        validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        // Return the token relation
        return tokenRel;
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var meta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();
        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(SubType.DEFAULT)
                .addBytesPerTransaction(meta.getBpt())
                .calculate();
    }
}

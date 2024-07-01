/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.service.token.AliasUtils.isAlias;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.isStakingAccount;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta.customFeeMetaFrom;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.TokenValidations.PERMIT_PAUSED;
import static com.hedera.node.app.service.token.impl.handlers.transfer.CryptoTransferExecutor.executeTransfer;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createFungibleTokenPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createNftPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createPendingAirdropRecord;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createPendingAirdropValue;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateFungibleTransfers;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateNftTransfers;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createAccountAmount;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.TokenAirdropsRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_AIRDROP}.
 */
@Singleton
public class TokenAirdropsHandler implements TransactionHandler {

    private final TokenAirdropValidator validator;
    private final CryptoTransferValidator transferValidator;

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropsHandler(
            @NonNull final TokenAirdropValidator validator, @NonNull final CryptoTransferValidator transferValidator) {
        this.validator = validator;
        this.transferValidator = transferValidator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.airdropsEnabled(), NOT_SUPPORTED);
        pureChecks(context.body());

        final var op = context.body().tokenAirdropOrThrow();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);

        for (final var transfers : op.tokenTransfers()) {
            final var tokenID = transfers.tokenOrThrow();
            final var tokenMeta = tokenStore.getTokenMeta(transfers.tokenOrElse(TokenID.DEFAULT));
            validateTruePreCheck(tokenMeta != null, INVALID_TOKEN_ID);
            checkFungibleTokenTransfers(tokenID, transfers.transfers(), context, accountStore);
            checkNftTransfers(tokenID, transfers.nftTransfers(), context, tokenMeta, accountStore);
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenAirdropOrThrow();
        validator.pureChecks(op);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.airdropsEnabled(), NOT_SUPPORTED);
        final var txn = context.body();
        final var op = txn.tokenAirdropOrThrow();
        final var pendingStore = context.storeFactory().writableStore(WritableAirdropStore.class);
        var recordBuilder = context.recordBuilders().getOrCreate(TokenAirdropsRecordBuilder.class);
        List<TokenTransferList> tokenTransferListList = new ArrayList<>();

        for (final var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.tokenOrThrow();

            boolean shouldExecuteCryptoTransfer = false;
            var transferListBuilder = TokenTransferList.newBuilder().token(tokenId);

            // process fungible token transfers
            if (!xfers.transfers().isEmpty()) {
                // 1. separate transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                var fungibleLists = separateFungibleTransfers(context, tokenId, xfers.transfers());
                var senderAccount = xfers.transfers().stream()
                        .filter(item -> item.amount() < 0)
                        .findFirst();

                // 2. create and save pending airdrops in to state
                fungibleLists.pendingFungibleAmounts().forEach(amount -> {
                    var pendingId = createFungibleTokenPendingAirdropId(
                            tokenId, senderAccount.orElseThrow().accountID(), amount.accountID());
                    var pendingValue = createPendingAirdropValue(amount.amount());
                    var record = createPendingAirdropRecord(pendingId, pendingValue);
                    pendingStore.put(pendingId, pendingValue);
                    recordBuilder.pendingAirdropList(record);
                });

                // 3. create account amounts and add to transfer list
                if (!fungibleLists.transferFungibleAmounts().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    List<AccountAmount> amounts = new LinkedList<>();
                    var receiversAmountList = fungibleLists.transferFungibleAmounts().stream()
                            .filter(item -> item.amount() > 0)
                            .toList();
                    var senderAmount = receiversAmountList.stream()
                            .mapToLong(AccountAmount::amount)
                            .sum();
                    var senderAccountAmount = createAccountAmount(
                            senderAccount.orElseThrow().accountIDOrThrow(),
                            -senderAmount,
                            senderAccount.get().isApproval());
                    amounts.add(senderAccountAmount);
                    amounts.addAll(receiversAmountList);

                    transferListBuilder.transfers(amounts);
                }
            }

            // process non-fungible tokens
            if (!xfers.nftTransfers().isEmpty()) {
                // 1. separate NFT transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                var nftLists = separateNftTransfers(context, tokenId, xfers.nftTransfers());

                // 2. create and save NFT pending airdrops in to state
                nftLists.pendingNftList().forEach(item -> {
                    var pendingId = createNftPendingAirdropId(
                            tokenId, item.serialNumber(), item.senderAccountID(), item.receiverAccountID());
                    pendingStore.put(pendingId, PendingAirdropValue.DEFAULT);
                    var record = createPendingAirdropRecord(pendingId, PendingAirdropValue.DEFAULT);
                    recordBuilder.pendingAirdropList(record);
                });

                // 3. add to transfer list
                if (!nftLists.transferNftList().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    transferListBuilder.nftTransfers(nftLists.transferNftList());
                }
            }

            // build transfer list and add it to tokenTransferListList
            if (shouldExecuteCryptoTransfer) {
                tokenTransferListList.add(transferListBuilder.build());
            }
        }

        // transfer tokens, that are not in pending state, if any...
        if (!tokenTransferListList.isEmpty()) {
            executeCryptoTransfer(context, tokenTransferListList, recordBuilder);
        }
    }

    private void executeCryptoTransfer(
            @NonNull final HandleContext context,
            List<TokenTransferList> tokenTransferList,
            CryptoTransferRecordBuilder recordBuilder) {
        var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(tokenTransferList)
                .build();

        final var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(cryptoTransferBody).build();

        final var transferContext = new TransferContextImpl(context, cryptoTransferBody, true);

        executeTransfer(syntheticCryptoTransferTxn, transferContext, context, transferValidator, recordBuilder);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var op = feeContext.body().tokenAirdropOrThrow();
        final var tokensConfig = feeContext.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.airdropsEnabled(), NOT_SUPPORTED);

        final var defaultAirdropFees =
                feeContext.feeCalculatorFactory().feeCalculator(SubType.DEFAULT).calculate();
        // TODO: add a comment why do we need that. This calculation includes the auto account creation + the crypto
        // transfer fees
        final var cryptoTransferFees = calculateCryptoTransferFees(feeContext, op.tokenTransfers());
        final var tokenAssociationFees = calculateTokenAssociationFees(feeContext, op);
        return combineFees(List.of(defaultAirdropFees, cryptoTransferFees, tokenAssociationFees));
    }

    // TODO: add documentation
    private Fees calculateCryptoTransferFees(
            @NonNull FeeContext feeContext, @NonNull List<TokenTransferList> tokenTransfers) {
        var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(tokenTransfers)
                .build();

        final var syntheticCryptoTransferTxn = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransferBody)
                .transactionID(feeContext.body().transactionID())
                .build();
        return feeContext.dispatchComputeFees(syntheticCryptoTransferTxn, feeContext.payer());
    }

    // TODO: add documentation
    private Fees calculateTokenAssociationFees(FeeContext feeContext, TokenAirdropTransactionBody op) {
        // Gather all the token associations that need to be created
        var tokenAssociationsMap = new HashMap<AccountID, Set<TokenID>>();
        final var tokenRelStore = feeContext.readableStore(ReadableTokenRelationStore.class);
        for (var transferList : op.tokenTransfers()) {
            final var tokenToTransfer = transferList.token();
            for (var transfer : transferList.transfers()) {
                if (tokenRelStore.get(transfer.accountID(), tokenToTransfer) == null) {
                    var list = tokenAssociationsMap.getOrDefault(transfer.accountID(), new HashSet<>());
                    list.add(tokenToTransfer);
                    tokenAssociationsMap.put(transfer.accountID(), list);
                }
            }
            for (var nftTransfer : transferList.nftTransfers()) {
                if (tokenRelStore.get(nftTransfer.receiverAccountID(), tokenToTransfer) == null) {
                    var list = tokenAssociationsMap.getOrDefault(nftTransfer.receiverAccountID(), new HashSet<>());
                    list.add(tokenToTransfer);
                    tokenAssociationsMap.put(nftTransfer.receiverAccountID(), list);
                }
            }
        }

        // Calculate the fees for each token association
        var feeList = new ArrayList<Fees>();
        for (var entry : tokenAssociationsMap.entrySet()) {
            final var tokenAssociateBody = TokenAssociateTransactionBody.newBuilder()
                    .account(entry.getKey())
                    .tokens(new ArrayList<>(entry.getValue()))
                    .build();

            final var syntheticTxn = TransactionBody.newBuilder()
                    .tokenAssociate(tokenAssociateBody)
                    .transactionID(feeContext.body().transactionID())
                    .build();

            feeList.add(feeContext.dispatchComputeFees(syntheticTxn, feeContext.payer()));
        }

        return combineFees(feeList);
    }

    private Fees combineFees(List<Fees> fees) {
        long networkFee = 0, nodeFee = 0, serviceFee = 0;
        for (var fee : fees) {
            networkFee += fee.networkFee();
            nodeFee += fee.nodeFee();
            serviceFee += fee.serviceFee();
        }
        return new Fees(nodeFee, networkFee, serviceFee);
    }

    /**
     * As part of pre-handle, token transfers in the transfer list are plausible.
     *
     * @param tokenID      The ID of the token we are transferring
     * @param transfers    The transfers to check
     * @param ctx          The context we gather signing keys into
     * @param accountStore The account store to use to look up accounts
     * @throws PreCheckException If the transaction is invalid
     */
    private void checkFungibleTokenTransfers(
            @NonNull final TokenID tokenID,
            @NonNull final List<AccountAmount> transfers,
            @NonNull final PreHandleContext ctx,
            @NonNull final ReadableAccountStore accountStore)
            throws PreCheckException {
        final var tokenStore = ctx.createStore(ReadableTokenStore.class);
        final var tokenRelStore = ctx.createStore(ReadableTokenRelationStore.class);
        // Fail if we have custom fees attached to the token
        validateTruePreCheck(tokenHasNoCustomFeesPaidByReceiver(tokenID, tokenStore), INVALID_TRANSACTION);
        // We're going to iterate over all the transfers in the transfer list. Each transfer is known as an
        // "account amount". Each of these represents the transfer of fungible token INTO a single account or OUT of a
        // single account.
        for (final var accountAmount : transfers) {
            // Given an accountId, we need to look up the associated account.
            final var accountId = validateAccountID(accountAmount.accountIDOrElse(AccountID.DEFAULT), null);
            final var account = accountStore.getAliasedAccountById(accountId);
            final var isCredit = accountAmount.amount() > 0;
            final var isDebit = accountAmount.amount() < 0;
            if (account != null) {
                if (isStakingAccount(ctx.configuration(), account.accountId()) && (isDebit || isCredit)) {
                    throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
                }

                if (isDebit) {
                    final var tokenRel = tokenRelStore.get(accountId, tokenID);
                    validateTruePreCheck(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
                    if (accountAmount.isApproval()) {
                        final var topLevelPayer = ctx.payer();
                        final var tokenAllowances = new ArrayList<>(account.tokenAllowances());
                        var haveExistingAllowance = false;
                        for (final var allowance : tokenAllowances) {
                            if (topLevelPayer.equals(allowance.spenderId()) && tokenID.equals(allowance.tokenId())) {
                                haveExistingAllowance = true;
                                final var newAllowanceAmount = allowance.amount() + accountAmount.amount();
                                validateTruePreCheck(newAllowanceAmount >= 0, AMOUNT_EXCEEDS_ALLOWANCE);
                            }
                        }
                        validateTruePreCheck(haveExistingAllowance, SPENDER_DOES_NOT_HAVE_ALLOWANCE);
                    } else {
                        validateTruePreCheck(
                                tokenRel.balance() >= Math.abs(accountAmount.amount()), INVALID_ACCOUNT_AMOUNTS);
                        // If the account is a hollow account, then we require a signature for it.
                        // It is possible that the hollow account has signed this transaction, in which case
                        // we need to finalize the hollow account by setting its key.
                        if (isHollow(account)) {
                            ctx.requireSignatureForHollowAccount(account);
                        } else {
                            ctx.requireKeyOrThrow(account.key(), INVALID_ACCOUNT_ID);
                        }
                    }
                } else if (isCredit && account.receiverSigRequired()) {
                    ctx.requireKeyOrThrow(account.key(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else if (isDebit) {
                // All debited accounts must be valid
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            }
        }
    }

    /**
     * As part of pre-handle, nft transfers in the transfer list are plausible.
     *
     * @param tokenID          The ID of the token we are transferring
     * @param nftTransfersList The nft transfers to check
     * @param context          The context we gather signing keys into
     * @param accountStore     The account store to use to look up accounts
     * @throws PreCheckException If the transaction is invalid
     */
    private void checkNftTransfers(
            final TokenID tokenID,
            final List<NftTransfer> nftTransfersList,
            final PreHandleContext context,
            final ReadableTokenStore.TokenMetadata tokenMeta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        final var nftStore = context.createStore(ReadableNftStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenRelStore = context.createStore(ReadableTokenRelationStore.class);
        final var token = getIfUsable(tokenID, tokenStore);

        validateTruePreCheck(tokenHasNoCustomFeesPaidByReceiver(tokenID, tokenStore), INVALID_TRANSACTION);

        for (final var nftTransfer : nftTransfersList) {
            // Validate accounts
            final var senderId = nftTransfer.senderAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(senderId, null);
            checkSender(senderId, nftTransfer, context, accountStore);
            final var senderAccount = accountStore.getAliasedAccountById(senderId);
            final var tokenRel = tokenRelStore.get(senderId, tokenID);
            validateTruePreCheck(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

            final var receiverId = nftTransfer.receiverAccountIDOrElse(AccountID.DEFAULT);
            validateAccountID(receiverId, null);
            checkReceiver(receiverId, context, tokenMeta, accountStore);
            final var receiverAccount = accountStore.getAliasedAccountById(receiverId);

            if (senderAccount == null || receiverAccount == null) {
                throw new PreCheckException(INVALID_TRANSACTION_BODY);
            }

            final var nft = nftStore.get(tokenID, nftTransfer.serialNumber());
            validateTrue(nft != null, INVALID_NFT_ID);

            if (nftTransfer.isApproval()) {
                // If isApproval flag is set then the spender account must have paid for the transaction.
                // The transfer list specifies the owner who granted allowance as sender
                // check if the allowances from the sender account has the payer account as spender
                validateSpenderHasAllowance(senderAccount, context.payer(), tokenID, nft);
            }

            // owner of nft should match the sender in transfer list
            if (nft.hasOwnerId()) {
                validateTrue(nft.ownerId() != null, INVALID_NFT_ID);
                validateTrue(nft.ownerId().equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            } else {
                final var treasuryId = token.treasuryAccountId();
                validateTrue(treasuryId != null, INVALID_ACCOUNT_ID);
                validateTrue(treasuryId.equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            }
        }
    }

    private void checkSender(
            final AccountID senderId,
            final NftTransfer nftTransfer,
            final PreHandleContext meta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        checkPayer(senderId, meta);
        // Lookup the sender account and verify it.
        final var senderAccount = accountStore.getAliasedAccountById(senderId);
        if (senderAccount == null) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }

        // If the sender account is immutable, then we throw an exception.
        final var key = senderAccount.key();
        if (key == null || !isValid(key)) {
            // If the sender account has no key, then fail with ACCOUNT_IS_IMMUTABLE.
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        } else if (!nftTransfer.isApproval()) {
            meta.requireKey(key);
        }
    }

    private void checkReceiver(
            final AccountID receiverId,
            final PreHandleContext meta,
            final ReadableTokenStore.TokenMetadata tokenMeta,
            final ReadableAccountStore accountStore)
            throws PreCheckException {

        // Lookup the receiver account and verify it.
        final var receiverAccount = accountStore.getAliasedAccountById(receiverId);
        if (receiverAccount == null) {
            // It may be that the receiver account does not yet exist. If it is being addressed by alias,
            // then this is OK, as we will automatically create the account. Otherwise, fail.
            if (!isAlias(receiverId)) {
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            } else {
                return;
            }
        }
        final var receiverKey = receiverAccount.key();
        if (isStakingAccount(meta.configuration(), receiverAccount.accountId())) {
            // If the receiver account has no key, then fail with ACCOUNT_IS_IMMUTABLE.
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        } else if (receiverAccount.receiverSigRequired()) {
            // If receiverSigRequired is set, and if there is no key on the receiver's account, then fail with
            // INVALID_TRANSFER_ACCOUNT_ID. Otherwise, add the key.
            meta.requireKeyOrThrow(receiverKey, INVALID_TRANSFER_ACCOUNT_ID);
        } else if (tokenMeta.hasRoyaltyWithFallback()) {
            // It may be that this transfer has royalty fees associated with it. If it does, we throw an error as
            // Token Airdrops does not support royalty with fallback fees
            throw new PreCheckException(INVALID_TRANSACTION);
        }
    }

    private void checkPayer(AccountID sender, PreHandleContext context) throws PreCheckException {
        if (context.payer() != sender) {
            context.requireKeyOrThrow(context.payerKey(), INVALID_ACCOUNT_ID);
        }
    }

    private void validateSpenderHasAllowance(
            final Account owner, final AccountID spender, final TokenID tokenId, final Nft nft) {
        final var approveForAllAllowances = owner.approveForAllNftAllowances();
        final var allowance = AccountApprovalForAllAllowance.newBuilder()
                .spenderId(spender)
                .tokenId(tokenId)
                .build();
        if (!approveForAllAllowances.contains(allowance)) {
            final var approvedSpender = nft.spenderId();
            validateTrue(approvedSpender != null && approvedSpender.equals(spender), SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        }
    }

    private boolean tokenHasNoCustomFeesPaidByReceiver(TokenID tokenId, ReadableTokenStore tokenStore) {
        final var token = getIfUsable(tokenId, tokenStore, PERMIT_PAUSED);
        final var feeMeta = customFeeMetaFrom(token);
        if (feeMeta.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
            for (var fee : feeMeta.customFees()) {
                if (fee.hasFractionalFee()
                        && !requireNonNull(fee.fractionalFee()).netOfTransfers()) {
                    return false;
                }
            }
        } else if (feeMeta.tokenType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            for (var fee : feeMeta.customFees()) {
                if (fee.hasRoyaltyFee()) {
                    return false;
                }
            }
        }
        return true;
    }
}

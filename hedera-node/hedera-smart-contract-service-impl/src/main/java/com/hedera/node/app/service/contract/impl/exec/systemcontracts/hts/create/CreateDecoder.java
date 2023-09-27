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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.node.app.service.contract.impl.exec.utils.IdUtils.asContract;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.utils.KeyValueWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenExpiryWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenKeyWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CreateDecoder {

    @Inject
    public CreateDecoder() {
        // Dagger2
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_TOKEN} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleToken(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_TOKEN.decodeCall(encoded);
        return bodyOf(createToken(call.get(0), call.get(1), call.get(2), addressIdConverter));
    }

    private TransactionBody bodyOf(@NonNull final TokenCreateTransactionBody.Builder tokenCreate) {
        return TransactionBody.newBuilder().tokenCreation(tokenCreate).build();
    }

    private TokenCreateTransactionBody.Builder createToken(
            @NonNull Tuple hederaToken,
            long initialTotalSupply,
            int decimals,
            @NonNull final AddressIdConverter addressIdConverter) {
        final TokenCreateWrapper tokenCreateWrapper =
                getTokenCreateWrapperFungible(hederaToken, true, initialTotalSupply, decimals, addressIdConverter);

        final var txnBodyBuilder = TokenCreateTransactionBody.newBuilder();

        txnBodyBuilder.name(tokenCreateWrapper.getName());
        txnBodyBuilder.symbol(tokenCreateWrapper.getSymbol());
        txnBodyBuilder.decimals(tokenCreateWrapper.getDecimals());
        txnBodyBuilder.tokenType(TokenType.FUNGIBLE_COMMON);
        txnBodyBuilder.supplyType(
                tokenCreateWrapper.isSupplyTypeFinite() ? TokenSupplyType.FINITE : TokenSupplyType.INFINITE);
        txnBodyBuilder.maxSupply(tokenCreateWrapper.getMaxSupply());
        txnBodyBuilder.initialSupply(tokenCreateWrapper.getInitSupply());

        // checks for treasury
        if (tokenCreateWrapper.getTreasury() != null) {
            txnBodyBuilder.treasury(tokenCreateWrapper.getTreasury());
        }

        tokenCreateWrapper.getTokenKeys().forEach(tokenKeyWrapper -> {
            final var key = tokenKeyWrapper.key().asGrpc();
            if (tokenKeyWrapper.isUsedForAdminKey()) {
                txnBodyBuilder.adminKey(key);
            }
            if (tokenKeyWrapper.isUsedForKycKey()) {
                txnBodyBuilder.kycKey(key);
            }
            if (tokenKeyWrapper.isUsedForFreezeKey()) {
                txnBodyBuilder.freezeKey(key);
            }
            if (tokenKeyWrapper.isUsedForWipeKey()) {
                txnBodyBuilder.wipeKey(key);
            }
            if (tokenKeyWrapper.isUsedForSupplyKey()) {
                txnBodyBuilder.supplyKey(key);
            }
            if (tokenKeyWrapper.isUsedForFeeScheduleKey()) {
                txnBodyBuilder.feeScheduleKey(key);
            }
            if (tokenKeyWrapper.isUsedForPauseKey()) {
                txnBodyBuilder.pauseKey(key);
            }
        });
        txnBodyBuilder.freezeDefault(tokenCreateWrapper.isFreezeDefault());
        txnBodyBuilder.memo(tokenCreateWrapper.getMemo());

        // checks for expiry
        if (tokenCreateWrapper.getExpiry().second() != 0) {
            txnBodyBuilder.expiry(Timestamp.newBuilder()
                    .seconds(tokenCreateWrapper.getExpiry().second())
                    .build());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewAccount() != null) {
            txnBodyBuilder.autoRenewAccount(tokenCreateWrapper.getExpiry().autoRenewAccount());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewPeriod() != null) {
            txnBodyBuilder.autoRenewPeriod(tokenCreateWrapper.getExpiry().autoRenewPeriod());
        }
        return txnBodyBuilder;
    }

    private static TokenCreateWrapper getTokenCreateWrapperFungible(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final long initSupply,
            final int decimals,
            @NonNull final AddressIdConverter addressIdConverter) {

        final var tokenName = (String) tokenCreateStruct.get(0);
        final var tokenSymbol = (String) tokenCreateStruct.get(1);
        final var tokenTreasury = addressIdConverter.convert(tokenCreateStruct.get(2));
        final var memo = (String) tokenCreateStruct.get(3);
        final var isSupplyTypeFinite = (Boolean) tokenCreateStruct.get(4);
        final var maxSupply = (long) tokenCreateStruct.get(5);
        final var isFreezeDefault = (Boolean) tokenCreateStruct.get(6);
        final var tokenKeys = decodeTokenKeys(tokenCreateStruct.get(7), addressIdConverter);
        final var tokenExpiry = decodeTokenExpiry(tokenCreateStruct.get(8), addressIdConverter);

        return new TokenCreateWrapper(
                isFungible,
                tokenName,
                tokenSymbol,
                tokenTreasury.accountNum() != 0 ? tokenTreasury : null,
                memo,
                isSupplyTypeFinite,
                initSupply,
                decimals,
                maxSupply,
                isFreezeDefault,
                tokenKeys,
                tokenExpiry);
    }

    private static List<TokenKeyWrapper> decodeTokenKeys(
            @NonNull final Tuple[] tokenKeysTuples, @NonNull final AddressIdConverter addressIdConverter) {
        final List<TokenKeyWrapper> tokenKeys = new ArrayList<>(tokenKeysTuples.length);
        for (final var tokenKeyTuple : tokenKeysTuples) {
            final var keyType = ((BigInteger) tokenKeyTuple.get(0)).intValue();
            final Tuple keyValueTuple = tokenKeyTuple.get(1);
            final var inheritAccountKey = (Boolean) keyValueTuple.get(0);
            final var contractId = asContract(addressIdConverter.convert(keyValueTuple.get(1)));
            final var ed25519 = (byte[]) keyValueTuple.get(2);
            final var ecdsaSecp256K1 = (byte[]) keyValueTuple.get(3);
            final var delegatableContractId = asContract(addressIdConverter.convert(keyValueTuple.get(4)));

            tokenKeys.add(new TokenKeyWrapper(
                    keyType,
                    new KeyValueWrapper(
                            inheritAccountKey,
                            contractId.contractNum() != 0 ? contractId : null,
                            ed25519,
                            ecdsaSecp256K1,
                            delegatableContractId.contractNum() != 0 ? delegatableContractId : null)));
        }

        return tokenKeys;
    }

    private static TokenExpiryWrapper decodeTokenExpiry(
            @NonNull final Tuple expiryTuple, @NonNull final AddressIdConverter addressIdConverter) {
        final var second = (long) expiryTuple.get(0);
        final var autoRenewAccount = addressIdConverter.convert(expiryTuple.get(1));
        final var autoRenewPeriod =
                Duration.newBuilder().seconds(expiryTuple.get(2)).build();
        return new TokenExpiryWrapper(
                second, autoRenewAccount.accountNum() == 0 ? null : autoRenewAccount, autoRenewPeriod);
    }
}

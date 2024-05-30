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

package com.hedera.node.app.fees.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.BootstrapConfig;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V0490FeeSchema extends Schema {
    public static final String MIDNIGHT_RATES_STATE_KEY = "MIDNIGHT_RATES";
    private static final Logger log = LogManager.getLogger(V0490FeeSchema.class);

    private static ExchangeRates fs;

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    public V0490FeeSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.singleton(MIDNIGHT_RATES_STATE_KEY, ExchangeRateSet.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var isGenesis = ctx.previousVersion() == null;
        final var midnightRatesState = ctx.newStates().getSingleton(MIDNIGHT_RATES_STATE_KEY);
        if (isGenesis) {
            // Set the initial exchange rates (from the bootstrap config) as the midnight rates
            final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
            final var exchangeRateSet = ExchangeRateSet.newBuilder()
                    .currentRate(ExchangeRate.newBuilder()
                            .centEquiv(bootstrapConfig.ratesCurrentCentEquiv())
                            .hbarEquiv(bootstrapConfig.ratesCurrentHbarEquiv())
                            .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesCurrentExpiry()))
                            .build())
                    .nextRate(ExchangeRate.newBuilder()
                            .centEquiv(bootstrapConfig.ratesNextCentEquiv())
                            .hbarEquiv(bootstrapConfig.ratesNextHbarEquiv())
                            .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesNextExpiry()))
                            .build())
                    .build();

            midnightRatesState.put(exchangeRateSet);
        }

        if (fs != null) {
            log.info("BBM: migrating fee service");

            var toState = ctx.newStates().getSingleton(V0490FeeSchema.MIDNIGHT_RATES_STATE_KEY);
            final var toRates = ExchangeRateSet.newBuilder()
                    .currentRate(ExchangeRate.newBuilder()
                            .centEquiv(fs.getCurrCentEquiv())
                            .hbarEquiv(fs.getCurrHbarEquiv())
                            .expirationTime(TimestampSeconds.newBuilder().seconds(fs.getCurrExpiry()))
                            .build())
                    .nextRate(ExchangeRate.newBuilder()
                            .centEquiv(fs.getNextCentEquiv())
                            .hbarEquiv(fs.getNextHbarEquiv())
                            .expirationTime(TimestampSeconds.newBuilder().seconds(fs.getNextExpiry()))
                            .build())
                    .build();
            toState.put(toRates);

            if (toState.isModified()) ((WritableSingletonStateBase) toState).commit();

            log.info("BBM: finished migrating fee service");
        } else {
            log.warn("BBM: no fee 'from' state found");
        }
        fs = null;
    }

    public static void setFs(ExchangeRates fs) {
        V0490FeeSchema.fs = fs;
    }
}

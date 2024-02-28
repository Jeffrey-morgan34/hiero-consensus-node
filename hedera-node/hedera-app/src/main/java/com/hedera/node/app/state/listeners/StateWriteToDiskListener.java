/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.listeners;

import com.hedera.node.app.service.file.impl.WritableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.WritableFreezeStore;
import com.hedera.node.app.service.networkadmin.impl.handlers.FreezeUpgradeActions;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listener that will be notified with {@link
 * StateWriteToDiskCompleteNotification} when state is
 * written to disk. This writes {@code NOW_FROZEN_MARKER} to disk when upgrade is pending
 */
@Singleton
public class StateWriteToDiskListener implements StateWriteToDiskCompleteListener {
    private static final Logger log = LogManager.getLogger(StateWriteToDiskListener.class);

    private final WorkingStateAccessor stateAccessor;
    private final Executor executor;
    private final ConfigProvider configProvider;

    @Inject
    public StateWriteToDiskListener(
            @NonNull final WorkingStateAccessor stateAccessor,
            @NonNull @Named("FreezeService") final Executor executor,
            @NonNull final ConfigProvider configProvider) {
        this.stateAccessor = stateAccessor;
        this.executor = executor;
        this.configProvider = configProvider;
    }

    @Override
    public void notify(final StateWriteToDiskCompleteNotification notification) {
        if (notification.isFreezeState()) {
            log.info(
                    "Notification Received: Freeze State Finished. "
                            + "consensusTimestamp: {}, roundNumber: {}, sequence: {}",
                    notification.getConsensusTimestamp(),
                    notification.getRoundNumber(),
                    notification.getSequence());
            final var writableStoreFactory = new WritableStoreFactory(stateAccessor.getHederaState(), FreezeService.NAME);
            final var upgradeActions = new FreezeUpgradeActions(
                    configProvider.getConfiguration().getConfigData(NetworkAdminConfig.class),
                    writableStoreFactory.getStore(WritableFreezeStore.class),
                    executor,
                    writableStoreFactory.getStore(WritableUpgradeFileStore.class));
            log.info("Externalizing freeze if upgrade is pending");
            upgradeActions.externalizeFreezeIfUpgradePending();
        }
    }
}

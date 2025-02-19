// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.state.listeners;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.app.service.networkadmin.impl.handlers.ReadableFreezeUpgradeActions;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.EntityIdFactory;
import com.swirlds.state.lifecycle.StartupNetworks;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listener that will be notified with {@link StateWriteToDiskCompleteNotification} when state is
 * written to disk. This writes {@code NOW_FROZEN_MARKER} to disk when upgrade is pending
 */
@Singleton
public class WriteStateToDiskListener implements StateWriteToDiskCompleteListener {
    private static final Logger log = LogManager.getLogger(WriteStateToDiskListener.class);

    private final Supplier<AutoCloseableWrapper<State>> stateAccessor;
    private final Executor executor;
    private final ConfigProvider configProvider;
    private final StartupNetworks startupNetworks;
    private final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory;
    private final EntityIdFactory idFactory;

    @Inject
    public WriteStateToDiskListener(
            @NonNull final Supplier<AutoCloseableWrapper<State>> stateAccessor,
            @NonNull @Named("FreezeService") final Executor executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final StartupNetworks startupNetworks,
            @NonNull final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory,
            @NonNull final EntityIdFactory idFactory) {
        this.stateAccessor = requireNonNull(stateAccessor);
        this.executor = requireNonNull(executor);
        this.configProvider = requireNonNull(configProvider);
        this.startupNetworks = requireNonNull(startupNetworks);
        this.softwareVersionFactory = softwareVersionFactory;
        this.idFactory = requireNonNull(idFactory);
    }

    @Override
    public void notify(@NonNull final StateWriteToDiskCompleteNotification notification) {
        if (notification.isFreezeState()) {
            log.info(
                    "StateWriteToDiskCompleteNotification Received : Freeze State Finished. "
                            + "consensusTimestamp: {}, roundNumber: {}, sequence: {}",
                    notification.getConsensusTimestamp(),
                    notification.getRoundNumber(),
                    notification.getSequence());
            try (final var wrappedState = stateAccessor.get()) {
                final var readableStoreFactory = new ReadableStoreFactory(wrappedState.get(), softwareVersionFactory);
                final var readableFreezeStore = readableStoreFactory.getStore(ReadableFreezeStore.class);
                final var readableUpgradeFileStore = readableStoreFactory.getStore(ReadableUpgradeFileStore.class);
                final var readableNodeStore = readableStoreFactory.getStore(ReadableNodeStore.class);
                final var readableStakingInfoStore = readableStoreFactory.getStore(ReadableStakingInfoStore.class);

                final var upgradeActions = new ReadableFreezeUpgradeActions(
                        configProvider.getConfiguration(),
                        readableFreezeStore,
                        executor,
                        readableUpgradeFileStore,
                        readableNodeStore,
                        readableStakingInfoStore,
                        idFactory);
                log.info("Externalizing freeze if upgrade is pending");
                upgradeActions.externalizeFreezeIfUpgradePending();
            } catch (Exception e) {
                log.error("Error while responding to freeze state notification", e);
            }
        }
        startupNetworks.archiveStartupNetworks();
    }
}

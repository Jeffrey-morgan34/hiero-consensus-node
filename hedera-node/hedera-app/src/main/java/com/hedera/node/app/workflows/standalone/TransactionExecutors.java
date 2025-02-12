// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.workflows.standalone;

import static com.hedera.node.app.spi.AppContext.Gossip.UNAVAILABLE_GOSSIP;
import static com.hedera.node.app.workflows.standalone.impl.NoopVerificationStrategies.NOOP_VERIFICATION_STRATEGIES;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.hints.impl.HintsLibraryImpl;
import com.hedera.node.app.hints.impl.HintsServiceImpl;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.state.recordcache.LegacyListRecordSource;
import com.hedera.node.app.throttle.AppThrottleFactory;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.MerkleNodeState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.EntityIdFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * A factory for creating {@link TransactionExecutor} instances.
 */
public enum TransactionExecutors {
    TRANSACTION_EXECUTORS;

    public static final String MAX_SIGNED_TXN_SIZE_PROPERTY = "executor.maxSignedTxnSize";

    /**
     * A strategy to bind and retrieve {@link OperationTracer} scoped to a thread.
     */
    public interface TracerBinding extends Supplier<List<OperationTracer>> {
        void runWhere(@NonNull List<OperationTracer> tracers, @NonNull Runnable runnable);
    }

    /**
     * The properties to use when creating a new {@link TransactionExecutor}.
     * @param state the {@link State} to use
     * @param appProperties the properties to use
     * @param customTracerBinding the custom tracer binding to use
     * @param customOps the custom operations to use
     */
    public record Properties(
            @NonNull MerkleNodeState state,
            @NonNull Map<String, String> appProperties,
            @Nullable TracerBinding customTracerBinding,
            @NonNull Set<Operation> customOps,
            @NonNull Function<SemanticVersion, SoftwareVersion> softwareVersionFactory) {
        /**
         * Create a new {@link Builder} instance.
         * @return a new {@link Builder} instance
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Builder for {@link Properties}.
         */
        public static class Builder {
            private MerkleNodeState state;
            private TracerBinding customTracerBinding;
            private final Map<String, String> appProperties = new HashMap<>();
            private final Set<Operation> customOps = new HashSet<>();
            private Function<SemanticVersion, SoftwareVersion> softwareVersionFactory;

            /**
             * Set the required {@link State} field.
             */
            public Builder state(@NonNull final MerkleNodeState state) {
                this.state = requireNonNull(state);
                return this;
            }

            /**
             * Add or override a single property.
             */
            public Builder appProperty(@NonNull final String key, @NonNull final String value) {
                requireNonNull(key);
                requireNonNull(value);
                this.appProperties.put(key, value);
                return this;
            }

            /**
             * Add/override multiple properties at once.
             */
            public Builder appProperties(@NonNull final Map<String, String> properties) {
                requireNonNull(properties);
                this.appProperties.putAll(properties);
                return this;
            }

            /**
             * Set the optional {@link TracerBinding}.
             */
            public Builder customTracerBinding(@Nullable final TracerBinding customTracerBinding) {
                this.customTracerBinding = customTracerBinding;
                return this;
            }

            /**
             * Set the custom operations in bulk.
             */
            public Builder customOps(@NonNull final Set<? extends Operation> customOps) {
                requireNonNull(customOps);
                this.customOps.addAll(customOps);
                return this;
            }

            /**
             * Add a single custom operation.
             */
            public Builder addCustomOp(@NonNull final Operation customOp) {
                requireNonNull(customOp);
                this.customOps.add(customOp);
                return this;
            }

            /**
             * Set the software version factory.
             */
            public Builder softwareVersionFactory(
                    @NonNull final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory) {
                this.softwareVersionFactory = requireNonNull(softwareVersionFactory);
                return this;
            }

            /**
             * Build and return the immutable {@link Properties} record.
             */
            public Properties build() {
                if (state == null) {
                    throw new IllegalStateException("State must not be null");
                }
                return new Properties(
                        state,
                        Map.copyOf(appProperties),
                        customTracerBinding,
                        Set.copyOf(customOps),
                        softwareVersionFactory);
            }
        }
    }

    /**
     * Creates a new {@link TransactionExecutor} based on the given {@link State} and properties.
     * @param properties the properties to use for the executor
     * @return a new {@link TransactionExecutor}
     */
    public TransactionExecutor newExecutor(
            @NonNull final Properties properties, @NonNull final EntityIdFactory entityIdFactory) {
        requireNonNull(properties);
        return newExecutor(
                properties.state(),
                properties.appProperties(),
                properties.customTracerBinding(),
                properties.customOps(),
                properties.softwareVersionFactory(),
                entityIdFactory);
    }

    /**
     * Creates a new {@link TransactionExecutor}.
     * @param state the {@link State} to use
     * @param properties the properties to use
     * @param customTracerBinding the custom tracer binding to use
     * @param customOps the custom operations to use
     * @return a new {@link TransactionExecutor}
     */
    private TransactionExecutor newExecutor(
            @NonNull final MerkleNodeState state,
            @NonNull final Map<String, String> properties,
            @Nullable final TracerBinding customTracerBinding,
            @NonNull final Set<Operation> customOps,
            @NonNull final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory,
            @NonNull final EntityIdFactory entityIdFactory) {
        final var tracerBinding =
                customTracerBinding != null ? customTracerBinding : DefaultTracerBinding.DEFAULT_TRACER_BINDING;
        final var executor = newExecutorComponent(
                state, properties, tracerBinding, customOps, softwareVersionFactory, entityIdFactory);
        executor.stateNetworkInfo().initFrom(state);
        executor.initializer().accept(state);
        final var exchangeRateManager = executor.exchangeRateManager();
        return (transactionBody, consensusNow, operationTracers) -> {
            final var dispatch = executor.standaloneDispatchFactory().newDispatch(state, transactionBody, consensusNow);
            tracerBinding.runWhere(List.of(operationTracers), () -> executor.dispatchProcessor()
                    .processDispatch(dispatch));
            final var recordSource = dispatch.stack()
                    .buildHandleOutput(consensusNow, exchangeRateManager.exchangeRates())
                    .recordSourceOrThrow();
            return ((LegacyListRecordSource) recordSource).precomputedRecords();
        };
    }

    private ExecutorComponent newExecutorComponent(
            @NonNull final State state,
            @NonNull final Map<String, String> properties,
            @NonNull final TracerBinding tracerBinding,
            @NonNull final Set<Operation> customOps,
            @NonNull final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory,
            @NonNull final EntityIdFactory entityIdFactory) {
        final var bootstrapConfigProvider = new BootstrapConfigProviderImpl();
        final var bootstrapConfig = bootstrapConfigProvider.getConfiguration();
        final var configProvider = new ConfigProviderImpl(false, null, properties);
        final AtomicReference<ExecutorComponent> componentRef = new AtomicReference<>();

        var defaultNodeInfo = new NodeInfoImpl(0, entityIdFactory.newAccountId(3L), 10, List.of(), Bytes.EMPTY);

        final var appContext = new AppContextImpl(
                InstantSource.system(),
                new AppSignatureVerifier(
                        bootstrapConfig.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())),
                UNAVAILABLE_GOSSIP,
                bootstrapConfigProvider::getConfiguration,
                () -> defaultNodeInfo,
                () -> NO_OP_METRICS,
                new AppThrottleFactory(
                        configProvider::getConfiguration,
                        () -> state,
                        () -> componentRef.get().throttleServiceManager().activeThrottleDefinitionsOrThrow(),
                        ThrottleAccumulator::new,
                        softwareVersionFactory),
                () -> componentRef.get().appFeeCharging(),
                entityIdFactory);
        final var contractService = new ContractServiceImpl(
                appContext, NO_OP_METRICS, NOOP_VERIFICATION_STRATEGIES, tracerBinding, customOps);
        final var fileService = new FileServiceImpl();
        final var scheduleService = new ScheduleServiceImpl(appContext);
        final var hintsService = new HintsServiceImpl(
                NO_OP_METRICS, ForkJoinPool.commonPool(), appContext, new HintsLibraryImpl(), bootstrapConfig);
        final var component = DaggerExecutorComponent.builder()
                .appContext(appContext)
                .configProviderImpl(configProvider)
                .bootstrapConfigProviderImpl(bootstrapConfigProvider)
                .hintsService(hintsService)
                .fileServiceImpl(fileService)
                .scheduleService(scheduleService)
                .contractServiceImpl(contractService)
                .scheduleServiceImpl(scheduleService)
                .hintsService(hintsService)
                .metrics(NO_OP_METRICS)
                .throttleFactory(appContext.throttleFactory())
                .maxSignedTxnSize(Optional.ofNullable(properties.get(MAX_SIGNED_TXN_SIZE_PROPERTY))
                        .map(Integer::parseInt)
                        .orElse(Hedera.MAX_SIGNED_TXN_SIZE))
                .build();
        componentRef.set(component);
        return component;
    }

    /**
     * The default {@link TracerBinding} implementation that uses a {@link ThreadLocal}.
     */
    private enum DefaultTracerBinding implements TracerBinding {
        DEFAULT_TRACER_BINDING;

        private static final ThreadLocal<List<OperationTracer>> OPERATION_TRACERS = ThreadLocal.withInitial(List::of);

        @Override
        public void runWhere(@NonNull final List<OperationTracer> tracers, @NonNull final Runnable runnable) {
            OPERATION_TRACERS.set(tracers);
            runnable.run();
        }

        @Override
        public List<OperationTracer> get() {
            return OPERATION_TRACERS.get();
        }
    }

    private static final Metrics NO_OP_METRICS = new NoOpMetrics();
}

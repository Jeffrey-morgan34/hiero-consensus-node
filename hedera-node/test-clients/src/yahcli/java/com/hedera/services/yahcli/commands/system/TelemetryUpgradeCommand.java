// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.yahcli.commands.system;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.UpgradeHelperSuite;
import com.hedera.services.yahcli.suites.Utils;
import com.swirlds.common.utility.CommonUtils;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "upgrade-telemetry",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Upgrades telemetry via NMT")
public class TelemetryUpgradeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    private Yahcli yahcli;

    @CommandLine.Option(
            names = {"-f", "--upgrade-file-num"},
            paramLabel = "Number of the telemetry upgrade ZIP file",
            defaultValue = "159")
    private String upgradeFileNum;

    @CommandLine.Option(
            names = {"-h", "--upgrade-zip-hash"},
            paramLabel = "Hex-encoded SHA-384 hash of the telemetry upgrade ZIP")
    private String upgradeFileHash;

    @CommandLine.Option(
            names = {"-s", "--start-time"},
            paramLabel = "Telemetry upgrade start time in UTC (yyyy-MM-dd.HH:mm:ss)")
    private String startTime;

    @Override
    public Integer call() throws Exception {
        final var config = ConfigUtils.configFrom(yahcli);

        final var upgradeFile = "0.0." + upgradeFileNum;
        final var unhexedHash = CommonUtils.unhex(upgradeFileHash);
        final var startInstant = Utils.parseFormattedInstant(startTime);
        final var delegate =
                new UpgradeHelperSuite(config.asSpecConfig(), unhexedHash, upgradeFile, startInstant, true);

        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == HapiSpec.SpecStatus.PASSED) {
            COMMON_MESSAGES.info("SUCCESS - NMT telemetry upgrade in motion from " + upgradeFile + " artifacts ZIP");
        } else {
            COMMON_MESSAGES.warn("FAILED - NMT telemetry upgrade is not in motion ");
            return 1;
        }

        return 0;
    }
}

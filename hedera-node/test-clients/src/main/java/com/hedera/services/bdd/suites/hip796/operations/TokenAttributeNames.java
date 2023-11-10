package com.hedera.services.bdd.suites.hip796.operations;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_UNDER_TEST;

/**
 * Self-explanatory factory methods that return the canonical {@link HapiSpecRegistry} names of
 * various token attributes that refer to entities like keys and accounts.
 *
 * <p>By relying on conventions for these names, we reduce the number of string literals
 * that need to explicitly appear in a given {@link HapiSpec} definition.
 */
public class TokenAttributeNames {
    public static String lockKeyOf(@NonNull final String tokenName) {
        return tokenName + "-LOCK-KEY";
    }

    public static String partitionKeyOf(@NonNull final String tokenName) {
        return tokenName + "-PARTITION-KEY";
    }

    public static String freezeKeyOf(@NonNull final String tokenName) {
        return tokenName + "-FREEZE-KEY";
    }

    public static String wipeKeyOf(@NonNull final String tokenName) {
        return tokenName + "-WIPE-KEY";
    }

    public static String pauseKeyOf(@NonNull final String tokenName) {
        return tokenName + "-PAUSE-KEY";
    }

    public static String kcyKeyOf(@NonNull final String tokenName) {
        return tokenName + "-KYC-KEY";
    }

    public static String supplyKeyOf(@NonNull final String tokenName) {
        return tokenName + "-SUPPLY-KEY";
    }

    public static String partitionManagementKeyOf(@NonNull final String tokenName) {
        return tokenName + "-PARTITION-MANAGEMENT-KEY";
    }

    public static String customFeeScheduleKeyOf(@NonNull final String tokenName) {
        return tokenName + "-FEE-SCHEDULE-KEY";
    }

    public static String adminKeyOf(@NonNull final String tokenName) {
        return tokenName + "-ADMIN-KEY";
    }

    public static String treasuryOf(@NonNull final String tokenName) {
        return tokenName + "-TREASURY";
    }

    public static String managementContractOf(@NonNull final String tokenName) {
        return  tokenName+  "-MANAGEMENT-CONTRACT";
    }

    public static String autoRenewAccountOf(@NonNull final String tokenName) {
        return tokenName + "-AUTO-RENEW-ACCOUNT";
    }

    public static String partitionWithDefaultTokenPrefixIfMissing(@NonNull final String tokenPartitionName) {
        return tokenPartitionName.contains("|") ? tokenPartitionName : partition(tokenPartitionName);
    }

    public static String partition(@NonNull final String partition) {
        return partition(TOKEN_UNDER_TEST, partition);
    }

    public static String partition(@NonNull final String tokenName, @NonNull final String partition) {
        return tokenName + "|" + partition;
    }
}

/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

/**
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public final class SettingConstants {

    /** name of the settings used file */
    static final String SETTING_USED_FILENAME = "settingsUsed.txt";

    static final int THROTTLE_TRANSACTION_QUEUE_SIZE_DEFAULT_VALUE = 100_000;
    static final int DEADLOCK_CHECK_PERIOD_DEFAULT_VALUE = 1000;
    static final boolean VERIFY_EVENT_SIGS_DEFAULT_VALUE = true;
    static final boolean SHOW_INTERNAL_STATS_DEFAULT_VALUE = false;
    static final boolean VERBOSE_STATISTICS_DEFAULT_VALUE = false;
    static final int STATS_BUFFER_SIZE_DEFAULT_VALUE = 100;
    static final int STATS_RECENT_SECONDS_DEFAULT_VALUE = 63;
    static final int TRANSACTION_MAX_BYTES_DEFAULT_VALUES = 6144;
    static final int MAX_ADDRESS_SIZE_ALLOWED_DEFAULT_VALUE = 1024;
    static final boolean LOAD_KEYS_FROM_PFX_FILES_DEFAULT_VALUE = true;
    static final int MAX_TRANSACTION_BYTES_PER_EVENT_DEFAULT_VALUE = 245760;
    static final int MAX_TRANSACTION_COUNT_PER_EVENT_DEFAULT_VALUE = 245760;

    private SettingConstants() {}
}

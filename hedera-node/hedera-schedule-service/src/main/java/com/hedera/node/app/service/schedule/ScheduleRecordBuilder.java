/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This record builder interface defines the methods needed to record schedule transactions as well
 * as adding a reference ID to the scheduled child transaction when it is executed.
 * <p>
 * It is important to note that any implementation cannot merely implement this interface.  Any
 * implementation must also implement all other record builder interfaces, as the child transaction
 * to which an instance of the implementation will be provided may be substantially any non-query
 * transaction other than a ScheduleCreate, ScheduleSign, or ScheduleDelete.
 */
public interface ScheduleRecordBuilder {
    @NonNull
    ScheduleRecordBuilder scheduleRef(ScheduleID scheduleRef);

    @NonNull
    ScheduleRecordBuilder scheduleID(ScheduleID scheduleID);

    @NonNull
    ScheduleRecordBuilder scheduledTransactionID(TransactionID scheduledTransactionID);
}

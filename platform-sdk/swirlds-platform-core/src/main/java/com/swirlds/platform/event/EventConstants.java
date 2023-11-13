/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import com.swirlds.common.system.NodeId;

public final class EventConstants {

    /**
     * Private constructor so that this class is never instantiated
     */
    private EventConstants() {}

    /**
     * the generation number used to represent that the generation is not defined.
     * an event's computed generation number is always non-negative.
     * in case it is used as a previousMarker generation, it means there is no previousMarker event
     */
    public static final long GENERATION_UNDEFINED = -1;
    /** the ID number used to represent that the ID is undefined */
    public static final NodeId CREATOR_ID_UNDEFINED = NodeId.UNDEFINED_NODE_ID;
    /** the smallest round an event can belong to */
    public static final long MINIMUM_ROUND_CREATED = 1;
}

/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.utility;

/**
 * This exception may be thrown if there is a problem during synchronization of merkle trees.
 */
public class MerkleSynchronizationException extends RuntimeException {

    public MerkleSynchronizationException(String message) {
        super(message);
    }

    public MerkleSynchronizationException(Exception ex) {
        super(ex);
    }

    public MerkleSynchronizationException(Throwable cause) {
        super(cause);
    }

    public MerkleSynchronizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

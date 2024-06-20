/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.schemas.SyntheticAccountCreator;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0500TokenSchema;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    public TokenServiceImpl() {
        // No-op
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V0490TokenSchema(new SyntheticAccountCreator()));
        registry.register(new V0500TokenSchema());
    }
}

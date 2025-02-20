// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.networkadmin.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.transaction.GetByKeyResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.spi.workflows.WorkflowException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#GET_BY_KEY}.
 * <p>
 * This network service call has been deprecated. Because protobufs promise backwards compatibility,
 * we cannot remove it. However, it should not be used.
 */
@Singleton
public class NetworkGetByKeyHandler extends FreeQueryHandler {
    @Inject
    public NetworkGetByKeyHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.getByKeyOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = GetByKeyResponse.newBuilder().header(header);
        return Response.newBuilder().getByKey(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) {
        requireNonNull(context);
        throw new WorkflowException(NOT_SUPPORTED);
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        // this code should never be executed, as validate() should fail before we get here
        requireNonNull(context);
        requireNonNull(header);
        throw new UnsupportedOperationException("Not supported");
    }
}

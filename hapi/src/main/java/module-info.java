/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

module com.hedera.node.hapi {
    exports com.hedera.hapi.node.base;
    exports com.hedera.hapi.node.base.codec;
    exports com.hedera.hapi.node.base.schema;
    exports com.hedera.hapi.node.consensus;
    exports com.hedera.hapi.node.consensus.codec;
    exports com.hedera.hapi.node.consensus.schema;
    exports com.hedera.hapi.node.contract;
    exports com.hedera.hapi.node.contract.codec;
    exports com.hedera.hapi.node.contract.schema;
    exports com.hedera.hapi.node.file;
    exports com.hedera.hapi.node.file.codec;
    exports com.hedera.hapi.node.file.schema;
    exports com.hedera.hapi.node.freeze;
    exports com.hedera.hapi.node.freeze.codec;
    exports com.hedera.hapi.node.freeze.schema;
    exports com.hedera.hapi.node.lambda;
    exports com.hedera.hapi.node.network;
    exports com.hedera.node.internal.network;
    exports com.hedera.hapi.node.network.codec;
    exports com.hedera.hapi.node.network.schema;
    exports com.hedera.hapi.node.scheduled;
    exports com.hedera.hapi.node.scheduled.codec;
    exports com.hedera.hapi.node.scheduled.schema;
    exports com.hedera.hapi.node.token;
    exports com.hedera.hapi.node.token.codec;
    exports com.hedera.hapi.node.token.schema;
    exports com.hedera.hapi.node.transaction;
    exports com.hedera.hapi.node.transaction.codec;
    exports com.hedera.hapi.node.transaction.schema;
    exports com.hedera.hapi.node.util;
    exports com.hedera.hapi.node.util.codec;
    exports com.hedera.hapi.node.util.schema;
    exports com.hedera.hapi.streams;
    exports com.hedera.hapi.streams.codec;
    exports com.hedera.hapi.streams.schema;
    exports com.hedera.hapi.node.addressbook;
    exports com.hedera.hapi.node.state.addressbook.codec;
    exports com.hedera.hapi.node.state.addressbook;
    exports com.hedera.hapi.node.state.consensus.codec;
    exports com.hedera.hapi.node.state.consensus;
    exports com.hedera.hapi.node.state.token;
    exports com.hedera.hapi.node.state.common;
    exports com.hedera.hapi.node.state.contract;
    exports com.hedera.hapi.node.state.file;
    exports com.hedera.hapi.node.state.hints;
    exports com.hedera.hapi.node.state.history;
    exports com.hedera.hapi.node.state.lambda;
    exports com.hedera.hapi.node.state.recordcache;
    exports com.hedera.hapi.node.state.recordcache.codec;
    exports com.hedera.hapi.node.state.blockrecords;
    exports com.hedera.hapi.node.state.blockrecords.codec;
    exports com.hedera.hapi.node.state.blockrecords.schema;
    exports com.hedera.hapi.node.state.blockstream;
    exports com.hedera.hapi.node.state.schedule;
    exports com.hedera.hapi.node.state.primitives;
    exports com.hedera.hapi.node.state.throttles;
    exports com.hedera.hapi.node.state.congestion;
    exports com.hedera.hapi.platform.event;
    exports com.hedera.services.stream.proto;
    exports com.hederahashgraph.api.proto.java;
    exports com.hederahashgraph.service.proto.java;
    exports com.hedera.hapi.util;
    exports com.hedera.hapi.block.stream;
    exports com.hedera.hapi.block.stream.input;
    exports com.hedera.hapi.block.stream.output;
    exports com.hedera.hapi.platform.state;
    exports com.hedera.hapi.node.state.roster;
    exports com.hedera.hapi.block.stream.schema;
    exports com.hedera.hapi.node.state.tss;
    exports com.hedera.hapi.services.auxiliary.tss;
    exports com.hedera.hapi.block.protoc;
    exports com.hedera.hapi.block.stream.protoc;
    exports com.hedera.hapi.block;
    exports com.hedera.hapi.services.auxiliary.tss.legacy;
    exports com.hedera.hapi.services.auxiliary.hints;
    exports com.hedera.hapi.services.auxiliary.history;
    exports com.hedera.hapi.platform.event.legacy;
    exports com.hedera.hapi.node.state.entity;

    requires transitive com.hedera.pbj.runtime;
    requires transitive com.google.common;
    requires transitive com.google.protobuf;
    requires transitive io.grpc.stub;
    requires transitive io.grpc;
    requires io.grpc.protobuf;
    requires org.antlr.antlr4.runtime;
    requires static com.github.spotbugs.annotations;
}

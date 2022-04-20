package com.hedera.services.state.virtual.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.beans.Transient;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.LongList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;

public class ScheduleEqualityVirtualValue implements VirtualValue {

	static final int CURRENT_VERSION = 1;

	static final long RUNTIME_CONSTRUCTABLE_ID = 0x1fe377366e3282f2L;

	private HashMap<String, Long> ids = new HashMap<>();

	private transient boolean immutable;


	public ScheduleEqualityVirtualValue() {
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || ScheduleEqualityVirtualValue.class != o.getClass()) {
			return false;
		}

		var that = (ScheduleEqualityVirtualValue) o;
		return Objects.equals(this.ids, that.ids);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(ids);
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(ScheduleEqualityVirtualValue.class)
				.add("ids", ids);
		return helper.toString();
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		int s = in.readInt();
		ids.clear();
		for (int x = 0; x <	s; ++x) {
			var key = new String(in.readNBytes(in.readInt()), StandardCharsets.UTF_8);
			ids.put(key, in.readLong());
		}
	}

	@Override
	public void deserialize(ByteBuffer in, int version) throws IOException {
		int s = in.getInt();
		ids.clear();
		for (int x = 0; x <	s; ++x) {
			byte[] keyBytes = new byte[in.getInt()];
			in.get(keyBytes);
			var key = new String(keyBytes, StandardCharsets.UTF_8);
			ids.put(key, in.getLong());
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(ids.size());
		for (var e : ids.entrySet()) {
			var keyBytes = e.getKey().getBytes(StandardCharsets.UTF_8);
			out.writeInt(keyBytes.length);
			out.writeByteArray(keyBytes);
			out.writeLong(e.getValue());
		}
	}

	@Override
	public void serialize(ByteBuffer out) throws IOException {
		out.putInt(ids.size());
		for (var e : ids.entrySet()) {
			var keyBytes = e.getKey().getBytes(StandardCharsets.UTF_8);
			out.putInt(keyBytes.length);
			out.put(keyBytes);
			out.putLong(e.getValue());
		}
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public ScheduleEqualityVirtualValue copy() {
		var fc = copyImpl();

		this.setImmutable(true);

		return fc;
	}

	public void add(String hash, long id) {
		throwIfImmutable("Cannot add to ids if it's immutable.");

		var cur = ids.get(hash);

		if (cur != null && cur.longValue() != id) {
			throw new IllegalStateException("multiple ids with same hash during add! " + cur + " and " + id);
		}

		ids.put(hash, id);
	}

	public void remove(String hash, long id) {
		throwIfImmutable("Cannot remove from ids if it's immutable.");

		var cur = ids.get(hash);

		if (cur != null && cur.longValue() != id) {
			throw new IllegalStateException("multiple ids with same hash during remove! " + cur + " and " + id);
		}

		ids.remove(hash);
	}

	public Map<String, Long> getIds() {
		return Collections.unmodifiableMap(ids);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean isImmutable() {
		return immutable;
	}

	@Transient
	protected final void setImmutable(final boolean immutable) {
		this.immutable = immutable;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void release() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualValue asReadOnly() {
		var c = copyImpl();
		c.setImmutable(true);
		return c;
	}

	private ScheduleEqualityVirtualValue copyImpl() {

		var fc = new ScheduleEqualityVirtualValue();

		fc.ids.putAll(ids);

		return fc;
	}
}

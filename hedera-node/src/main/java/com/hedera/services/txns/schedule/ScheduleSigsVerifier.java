package com.hedera.services.txns.schedule;
/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.keys.CharacteristicsFactory;
import com.hedera.services.keys.HederaKeyActivation;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.annotations.WorkingStateSigReqs;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;

import com.swirlds.common.crypto.TransactionSignature;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;

/**
 * Class that encapsulates checking schedule signatures.
 */
@Singleton
public class ScheduleSigsVerifier {

	private final SigRequirements workingSigReqs;
	private final CharacteristicsFactory characteristics;

	@VisibleForTesting
	InHandleActivationHelper.Activation activation = HederaKeyActivation::isActive;


	@Inject
	public ScheduleSigsVerifier(final @WorkingStateSigReqs SigRequirements workingSigReqs,
			final CharacteristicsFactory characteristics) {
		this.workingSigReqs = workingSigReqs;
		this.characteristics = characteristics;
	}

	public boolean areAllKeysActive(final ScheduleVirtualValue schedule) {
		final var scheduledTxn = schedule.ordinaryViewOfScheduledTxn();

		final var reqsResult = workingSigReqs.keysForOtherParties(
			scheduledTxn,
			CODE_ORDER_RESULT_FACTORY);

		if (reqsResult.hasErrorReport()) {
			return false;
		} else {

			final var activeCharacter = characteristics.inferredFor(scheduledTxn);

			final Function<byte[], TransactionSignature> ignoredSigsFn = publicKey -> INVALID_MISSING_SIG;

			final BiPredicate<JKey, TransactionSignature> activationTest = (key, sig) ->
					schedule.hasValidSignatureFor(key.primitiveKeyIfPresent());

			for (final var reqKey : reqsResult.getOrderedKeys()) {
				if (!activation.test(reqKey, ignoredSigsFn, activationTest, activeCharacter)) {
					return false;
				}
			}
		}

		return true;
	}

}

package com.hedera.services.state.merkle;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StakeInfoManagementTest {
	@Test
	void updatedStakeCannotExceedMaxStake() {
		final var subject = subjectWith(1L, 10L, 9L, 2L);
		subject.reviewElectionsFromJustFinishedPeriodAndRecomputeStakes();
		assertEquals(10L, subject.getStake());
	}

	@Test
	void updatedStakeIsZeroIfBelowMinStake() {
		final var subject = subjectWith(12L, 20L, 9L, 2L);
		subject.reviewElectionsFromJustFinishedPeriodAndRecomputeStakes();
		assertEquals(0L, subject.getStake());
	}

	@Test
	void updatedStakeIsSumOfRewardedAndNotRewardedIfWithinBounds() {
		final var subject = subjectWith(12L, 20L, 9L, 5L);
		subject.reviewElectionsFromJustFinishedPeriodAndRecomputeStakes();
		assertEquals(14L, subject.getStake());
	}


	@Test
	void stakeRewardStartIsMinimumOfStakedToRewardAndNewStake() {
		final var subject = subjectWith(12L, 20L, 9L, 5L, 16L);
		final var newStakeRewardStart = subject.reviewElectionsFromJustFinishedPeriodAndRecomputeStakes();
		assertEquals(9L, newStakeRewardStart);
		assertEquals(9L, subject.getStakeRewardStart());
	}

	private MerkleStakingInfo subjectWith(
			final long minStake,
			final long maxStake,
			final long stakedToReward,
			final long stakedToNotReward
	) {
		return subjectWith(minStake, maxStake, stakedToReward, stakedToNotReward, -1);
	}

	private MerkleStakingInfo subjectWith(
			final long minStake,
			final long maxStake,
			final long stakedToReward,
			final long stakedToNotReward,
			final long stakeRewardStart
	) {
		final var info = new MerkleStakingInfo();
		info.setMaxStake(maxStake);
		info.setMinStake(minStake);
		info.setStakeToReward(stakedToReward);
		info.setStakeToNotReward(stakedToNotReward);
		if (stakeRewardStart != -1) {
			info.setStakeRewardStart(stakeRewardStart);
		}
		return info;
	}
}

package com.hedera.services.ledger.accounts.staking;

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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;

import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PendingRewardsManagementTest {
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private EntityCreator creator;
	@Mock
	private PropertySource properties;
	@Mock
	private MerkleStakingInfo info;

	private EndOfStakingPeriodCalculator subject;

	@BeforeEach
	void setUp() {
		subject = new EndOfStakingPeriodCalculator(
				() -> accounts,
				() -> stakingInfos,
				() -> networkCtx,
				syntheticTxnFactory,
				recordsHistorian,
				creator,
				properties);
	}

	@Test
	void pendingRewardsIsUpdatedBasedOnLastPeriodRewardRateAndStakeRewardStart() {
		given800Balance(1_000_000_000_000L);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		given(networkCtx.getTotalStakedRewardStart()).willReturn(totalStakedRewardStart);
		given(properties.getLongProperty("staking.rewardRate")).willReturn(rewardRate);
		given(stakingInfos.keySet()).willReturn(Set.of(onlyNodeNum));
		given(stakingInfos.getForModify(onlyNodeNum)).willReturn(info);
		given(info.getStakeRewardStart()).willReturn(stakeRewardStart);
		given(info.updateRewardSumHistory(rewardRate / (totalStakedRewardStart / HBARS_TO_TINYBARS)))
				.willReturn(lastPeriodRewardRate);
		given(info.reviewElectionsFromJustFinishedPeriodAndRecomputeStakes()).willReturn(updatedStakeRewardStart);

		subject.updateNodes(Instant.EPOCH.plusSeconds(123_456));

		verify(networkCtx).increasePendingRewards((stakeRewardStart / 100_000_000) * lastPeriodRewardRate);
	}

	@Test
	void rewardRateIsZeroIfPendingRewardsExceed800Balance() {
		given800Balance(123);
		given(networkCtx.getPendingRewards()).willReturn(124L);

		Assertions.assertEquals(0, subject.effectiveRateForCurrentPeriod());
	}

	private void given800Balance(final long balance) {
		given(properties.getLongProperty("accounts.stakingRewardAccount")).willReturn(800L);
		given(accounts.get(EntityNum.fromLong(800)))
				.willReturn(MerkleAccountFactory.newAccount().balance(balance).get());
	}

	private static final long rewardRate = 100_000_000;
	private static final long stakeRewardStart = 666L * 100_000_000L;
	private static final long updatedStakeRewardStart = 777L * 100_000_000L;
	private static final long lastPeriodRewardRate = 100_000L;
	private static final long totalStakedRewardStart = 100_000_000_000L;
	private static final EntityNum onlyNodeNum = EntityNum.fromLong(123);
}

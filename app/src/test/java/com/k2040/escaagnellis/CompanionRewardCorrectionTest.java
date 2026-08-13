package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public class CompanionRewardCorrectionTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 2);

    @Test
    public void grantedRewardCanBeRevokedAndGrantedAgain() {
        CompanionEconomy economy = CompanionEconomy.defaultEconomy();
        CompanionState enabled = CompanionState.disabledDefault().withEnabled(true);

        CompanionEconomy.Result granted = economy.rewardDefaultPosition(enabled, DATE, 0);
        assertEquals(CompanionEconomy.Outcome.REWARD_GRANTED, granted.outcome);
        assertEquals(1L, granted.state.balance);
        assertTrue(granted.state.hasReward(DATE, 0));

        CompanionEconomy.Result revoked =
                economy.revokeDefaultPosition(granted.state, DATE, 0);
        assertEquals(CompanionEconomy.Outcome.REWARD_REVOKED, revoked.outcome);
        assertEquals(-1L, revoked.balanceDelta);
        assertEquals(0L, revoked.state.balance);
        assertFalse(revoked.state.hasReward(DATE, 0));

        CompanionEconomy.Result grantedAgain =
                economy.rewardDefaultPosition(revoked.state, DATE, 0);
        assertEquals(CompanionEconomy.Outcome.REWARD_GRANTED, grantedAgain.outcome);
        assertEquals(1L, grantedAgain.state.balance);
        assertTrue(grantedAgain.state.hasReward(DATE, 0));
    }

    @Test
    public void revokingOnePositionDoesNotAffectAnotherPosition() {
        CompanionEconomy economy = CompanionEconomy.defaultEconomy();
        CompanionState enabled = CompanionState.disabledDefault().withEnabled(true);
        CompanionState twoRewards = economy.rewardDefaultPosition(
                economy.rewardDefaultPosition(enabled, DATE, 0).state,
                DATE,
                1).state;

        CompanionEconomy.Result revoked =
                economy.revokeDefaultPosition(twoRewards, DATE, 0);

        assertEquals(1L, revoked.state.balance);
        assertFalse(revoked.state.hasReward(DATE, 0));
        assertTrue(revoked.state.hasReward(DATE, 1));
    }

    @Test
    public void missingRewardIsNotChanged() {
        CompanionEconomy economy = CompanionEconomy.defaultEconomy();
        CompanionState enabled = CompanionState.disabledDefault().withEnabled(true);

        CompanionEconomy.Result result = economy.revokeDefaultPosition(enabled, DATE, 0);

        assertEquals(CompanionEconomy.Outcome.REWARD_NOT_FOUND, result.outcome);
        assertEquals(0L, result.balanceDelta);
        assertEquals(enabled, result.state);
    }
}

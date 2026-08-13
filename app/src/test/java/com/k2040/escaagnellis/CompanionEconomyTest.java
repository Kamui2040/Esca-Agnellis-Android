package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;

public class CompanionEconomyTest {
    private final CompanionEconomy economy = CompanionEconomy.defaultEconomy();

    @Test
    public void reward_isDisabledUntilUserOptsIn() {
        CompanionState state = CompanionState.disabledDefault();

        CompanionEconomy.Result result = economy.rewardDefaultPosition(
                state,
                LocalDate.of(2026, 7, 1),
                0);

        assertEquals(CompanionEconomy.Outcome.DISABLED, result.outcome);
        assertSame(state, result.state);
        assertEquals(0L, result.balanceDelta);
    }

    @Test
    public void everyDefaultPositionIncludingExtrasAwardsSameAmountOnce() {
        CompanionState state = CompanionState.disabledDefault().withEnabled(true);
        LocalDate date = LocalDate.of(2026, 7, 1);

        for (int position = 0; position < PyramidScheme.TILE_COUNT; position++) {
            CompanionEconomy.Result result = economy.rewardDefaultPosition(state, date, position);
            assertEquals(CompanionEconomy.Outcome.REWARD_GRANTED, result.outcome);
            assertEquals(CompanionEconomy.DEFAULT_REWARD_PER_POSITION, result.balanceDelta);
            state = result.state;
        }

        assertEquals(
                PyramidScheme.TILE_COUNT * CompanionEconomy.DEFAULT_REWARD_PER_POSITION,
                state.balance);
        assertEquals(1, state.rewardLedger.size());

        CompanionEconomy.Result repeated = economy.rewardDefaultPosition(state, date, 0);
        assertEquals(CompanionEconomy.Outcome.ALREADY_REWARDED, repeated.outcome);
        assertSame(state, repeated.state);
        assertEquals(0L, repeated.balanceDelta);
    }

    @Test
    public void samePositionCanAwardOnDifferentDates() {
        CompanionState state = CompanionState.disabledDefault().withEnabled(true);

        state = economy.rewardDefaultPosition(state, LocalDate.of(2026, 7, 1), 16).state;
        state = economy.rewardDefaultPosition(state, LocalDate.of(2026, 7, 2), 16).state;

        assertEquals(2L, state.balance);
        assertEquals(2, state.rewardLedger.size());
    }

    @Test
    public void repeatableExtraPositionIsNotAValidRewardSource() {
        CompanionState state = CompanionState.disabledDefault().withEnabled(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> economy.rewardDefaultPosition(
                        state,
                        LocalDate.of(2026, 7, 1),
                        PyramidScheme.TILE_COUNT));
    }

    @Test
    public void purchaseCompletesWithoutAllowingNegativeBalance() {
        CompanionState state = CompanionState.disabledDefault().withEnabled(true);
        LocalDate date = LocalDate.of(2026, 7, 1);
        for (int position = 0; position < 3; position++) {
            state = economy.rewardDefaultPosition(state, date, position).state;
        }

        CompanionEconomy.Result completed = economy.purchaseInteraction(
                state,
                "feed_apple",
                2L,
                Instant.parse("2026-07-01T12:00:00Z"));
        assertEquals(CompanionEconomy.Outcome.PURCHASE_COMPLETED, completed.outcome);
        assertEquals(1L, completed.state.balance);
        assertEquals(-2L, completed.balanceDelta);
        assertEquals("feed_apple", completed.state.lastInteractionId);

        CompanionEconomy.Result insufficient = economy.purchaseInteraction(
                completed.state,
                "feed_apple",
                2L,
                Instant.parse("2026-07-01T12:01:00Z"));
        assertEquals(CompanionEconomy.Outcome.INSUFFICIENT_BALANCE, insufficient.outcome);
        assertSame(completed.state, insufficient.state);
        assertEquals(1L, insufficient.state.balance);
    }
}

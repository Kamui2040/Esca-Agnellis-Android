package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CompanionRewardGracePeriodTest {
    @Test
    public void deadlineIsExactlyFifteenSecondsAfterGrant() {
        assertEquals(
                16_000L,
                CompanionRewardGracePeriod.deadlineAfter(1_000L));
    }

    @Test
    public void deadlineRemainsActiveUntilButNotIncludingDeadline() {
        long deadline = CompanionRewardGracePeriod.deadlineAfter(1_000L);

        assertFalse(CompanionRewardGracePeriod.isActive(deadline, 999L));
        assertTrue(CompanionRewardGracePeriod.isActive(deadline, 1_000L));
        assertTrue(CompanionRewardGracePeriod.isActive(deadline, 15_999L));
        assertFalse(CompanionRewardGracePeriod.isActive(deadline, 16_000L));
        assertTrue(CompanionRewardGracePeriod.isExpired(deadline, 16_000L));
    }

    @Test
    public void invalidOrOverflowingTimesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionRewardGracePeriod.deadlineAfter(-1L));
        assertThrows(
                ArithmeticException.class,
                () -> CompanionRewardGracePeriod.deadlineAfter(Long.MAX_VALUE));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionRewardGracePeriod.isActive(0L, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionRewardGracePeriod.isActive(1L, -1L));
    }
}

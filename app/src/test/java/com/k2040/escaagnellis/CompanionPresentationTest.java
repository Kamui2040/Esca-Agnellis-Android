package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;

public class CompanionPresentationTest {
    @Test
    public void disabledAndExpiredStatesProduceStableIdleSnapshot() {
        Instant now = Instant.parse("2026-07-01T12:00:10Z");
        CompanionState disabled = CompanionState.disabledDefault();

        CompanionPresentation.Snapshot disabledSnapshot =
                CompanionPresentation.derive(disabled, now);
        assertFalse(disabledSnapshot.enabled);
        assertEquals(CompanionPresentation.POSE_IDLE, disabledSnapshot.poseId);
        assertNull(disabledSnapshot.reactionId);

        CompanionState enabled = disabled.withEnabled(true)
                .withReward(LocalDate.of(2026, 7, 1), 0, 1L)
                .withPurchase("feed_apple", 1L, now.minusSeconds(10).toEpochMilli());
        CompanionPresentation.Snapshot first = CompanionPresentation.derive(enabled, now);
        CompanionPresentation.Snapshot second = CompanionPresentation.derive(enabled, now);

        assertEquals(first, second);
        assertEquals(CompanionPresentation.POSE_IDLE, first.poseId);
        assertEquals(CompanionPresentation.MOOD_CONTENT, first.moodId);
        assertNull(first.reactionId);
    }

    @Test
    public void recentFeedAndInteractionUseStablePresentationIdentifiers() {
        Instant eventTime = Instant.parse("2026-07-01T12:00:00Z");
        CompanionState state = CompanionState.disabledDefault()
                .withEnabled(true)
                .withCompanionName("Luna")
                .withReward(LocalDate.of(2026, 7, 1), 0, 2L)
                .withPurchase("feed_apple", 1L, eventTime.toEpochMilli());

        CompanionPresentation.Snapshot eating = CompanionPresentation.derive(
                state,
                eventTime.plusMillis(1_000L));
        assertTrue(eating.enabled);
        assertEquals("Luna", eating.displayName);
        assertEquals(CompanionPresentation.POSE_EATING, eating.poseId);
        assertEquals(CompanionPresentation.MOOD_HAPPY, eating.moodId);
        assertEquals("feed_apple", eating.reactionId);
        assertEquals(1L, eating.balance);

        CompanionState petted = state.withReward(LocalDate.of(2026, 7, 1), 1, 1L)
                .withPurchase("pet", 1L, eventTime.plusSeconds(10).toEpochMilli());
        CompanionPresentation.Snapshot happy = CompanionPresentation.derive(
                petted,
                eventTime.plusSeconds(11));
        assertEquals(CompanionPresentation.POSE_HAPPY, happy.poseId);
        assertEquals("pet", happy.reactionId);
    }
}

package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CompanionPageStateTest {
    @Test
    public void newStateIsClosedAndCleared() {
        CompanionPageState state = new CompanionPageState();

        assertFalse(state.isOpen());
        assertNull(state.sessionPose());
        assertNull(state.displayedPose(0L));
        assertNull(state.reactionId(0L));
    }

    @Test
    public void newlyOpenedPageStartsSleeping() {
        CompanionPageState state = new CompanionPageState();

        state.open();

        assertTrue(state.isOpen());
        assertSame(CompanionPageState.Pose.SLEEPING, state.sessionPose());
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(0L));
    }

    @Test
    public void closingAndReopeningStartsAFreshSleepingSession() {
        CompanionPageState state = new CompanionPageState();

        state.open();
        assertTrue(state.startReaction("play", 100L));
        assertSame(CompanionPageState.Pose.HAPPY, state.displayedPose(100L));

        state.close();
        state.open();

        assertTrue(state.isOpen());
        assertSame(CompanionPageState.Pose.SLEEPING, state.sessionPose());
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(200L));
        assertEquals(0L, state.nextTransitionRemainingMillis(200L));
    }

    @Test
    public void treatTemporarilyDisplaysEatingAndThenIdle() {
        CompanionPageState state = openSleepingState();
        long start = 100L;

        assertTrue(state.startReaction("feed_treat", start));
        assertSame(CompanionPageState.Pose.EATING, state.displayedPose(start));
        assertEquals("feed_treat", state.reactionId(start));
        assertTrue(state.showsTreatBowl(start));
        assertFalse(state.showsPlayBall(start));
        assertFalse(state.showsCuddleHearts(start));

        long end = start + CompanionPageState.REACTION_DURATION_MS;
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(end));
        assertFalse(state.showsTreatBowl(end));
        assertFalse(state.showsPlayBall(end));
        assertFalse(state.showsCuddleHearts(end));
    }

    @Test
    public void playTemporarilyDisplaysHappyAndThenIdle() {
        CompanionPageState state = openSleepingState();
        long start = 200L;

        assertTrue(state.startReaction("play", start));
        assertSame(CompanionPageState.Pose.HAPPY, state.displayedPose(start));
        assertTrue(state.showsPlayBall(start));
        assertEquals(0f, state.reactionProgress(start), 0f);
        assertEquals(.5f, state.reactionProgress(
                start + CompanionPageState.REACTION_DURATION_MS / 2L), .01f);

        long end = start + CompanionPageState.REACTION_DURATION_MS;
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(end));
        assertFalse(state.showsPlayBall(end));
        assertFalse(state.showsTreatBowl(end));
        assertFalse(state.showsCuddleHearts(end));
        assertEquals(0f, state.reactionProgress(end), 0f);
    }

    @Test
    public void cuddleTemporarilyDisplaysCuddleAndThenIdle() {
        CompanionPageState state = openSleepingState();
        long start = 300L;

        assertTrue(state.startReaction("cuddle", start));
        assertSame(CompanionPageState.Pose.CUDDLE, state.displayedPose(start + 1L));
        assertTrue(state.showsCuddleHearts(start + 1L));

        long end = start + CompanionPageState.REACTION_DURATION_MS;
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(end));
        assertFalse(state.showsCuddleHearts(end));
        assertFalse(state.showsTreatBowl(end));
        assertFalse(state.showsPlayBall(end));
        assertNull(state.reactionId(end));
    }

    @Test
    public void expiredEffectsAndPropsStop() {
        CompanionPageState state = openSleepingState();
        long start = 400L;

        assertTrue(state.startReaction("play", start));

        long end = start + CompanionPageState.REACTION_DURATION_MS;
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(end));
        assertFalse(state.hasActiveReaction(end));
        assertEquals(0L, state.reactionRemainingMillis(end));
        assertEquals(0f, state.reactionProgress(end), 0f);
        assertFalse(state.showsPlayBall(end));
        assertFalse(state.showsTreatBowl(end));
        assertFalse(state.showsCuddleHearts(end));
    }

    @Test
    public void directFinishRemovesActiveReaction() {
        CompanionPageState state = openSleepingState();

        assertTrue(state.startReaction("cuddle", 500L));
        state.finishReaction();

        assertTrue(state.isOpen());
        assertSame(CompanionPageState.Pose.IDLE, state.sessionPose());
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(501L));
        assertNull(state.reactionId(501L));
        assertFalse(state.showsCuddleHearts(501L));
    }

    @Test
    public void closingClearsActiveReaction() {
        CompanionPageState state = openSleepingState();

        assertTrue(state.startReaction("cuddle", 500L));
        state.close();

        assertFalse(state.isOpen());
        assertNull(state.sessionPose());
        assertNull(state.displayedPose(501L));
        assertNull(state.reactionId(501L));
        assertEquals(0L, state.reactionRemainingMillis(501L));
        assertEquals(0f, state.reactionProgress(501L), 0f);
        assertFalse(state.showsTreatBowl(501L));
        assertFalse(state.showsPlayBall(501L));
        assertFalse(state.showsCuddleHearts(501L));
    }

    @Test
    public void reservationLocksEveryInteractionBeforeReactionCompletion() {
        CompanionPageState state = openSleepingState();

        assertTrue(state.tryBeginInteraction("feed_treat", 600L));
        assertTrue(state.interactionsLocked(600L));
        assertFalse(state.hasActiveReaction(600L));
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(600L));
        assertFalse(state.tryBeginInteraction("feed_treat", 600L));
        assertFalse(state.tryBeginInteraction("play", 600L));
        assertFalse(state.tryBeginInteraction("cuddle", 600L));
        assertFalse(state.completeInteraction("play"));

        assertTrue(state.completeInteraction("feed_treat"));
        assertFalse(state.completeInteraction("feed_treat"));
        assertTrue(state.interactionsLocked(600L));
        assertSame(CompanionPageState.Pose.EATING, state.displayedPose(600L));
    }

    @Test
    public void cancelledReservationUnlocksWithoutChangingPoseOrDeadline() {
        CompanionPageState state = openSleepingState();

        assertTrue(state.tryBeginInteraction("feed_treat", 700L));
        state.cancelInteraction("play");
        assertTrue(state.interactionsLocked(700L));

        state.cancelInteraction("feed_treat");
        assertFalse(state.interactionsLocked(700L));
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(700L));
        assertEquals(0L, state.nextTransitionRemainingMillis(700L));
        assertTrue(state.startReaction("play", 701L));
    }

    @Test
    public void closeInvalidatesPendingCompletionAndReopenStartsUnlocked() {
        CompanionPageState state = openSleepingState();

        assertTrue(state.tryBeginInteraction("cuddle", 750L));
        state.close();
        assertFalse(state.completeInteraction("cuddle"));

        state.open();
        assertFalse(state.interactionsLocked(751L));
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(751L));
    }

    @Test
    public void activeReactionIgnoresRapidRepeatedAndDifferentInteractions() {
        CompanionPageState state = openSleepingState();

        assertTrue(state.startReaction("feed_treat", 800L));
        assertFalse(state.startReaction("feed_treat", 801L));
        assertFalse(state.startReaction("play", 802L));
        assertFalse(state.startReaction("cuddle", 803L));
        assertSame(CompanionPageState.Pose.EATING, state.displayedPose(803L));
        assertEquals("feed_treat", state.reactionId(803L));
        assertTrue(state.showsTreatBowl(803L));
        assertFalse(state.showsPlayBall(803L));

        long deadline = 800L + CompanionPageState.REACTION_DURATION_MS;
        assertTrue(state.interactionsLocked(deadline - 1L));
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(deadline));
        assertFalse(state.interactionsLocked(deadline));

        assertTrue(state.startReaction("play", deadline));
        assertSame(CompanionPageState.Pose.HAPPY, state.displayedPose(deadline));
    }

    @Test
    public void reactionExpiresExactlyAtDeadline() {
        CompanionPageState state = openSleepingState();
        long start = 800L;
        long deadline = start + CompanionPageState.REACTION_DURATION_MS;

        assertTrue(state.startReaction("feed_treat", start));
        assertSame(CompanionPageState.Pose.EATING, state.displayedPose(deadline - 1L));
        assertTrue(state.hasActiveReaction(deadline - 1L));
        assertEquals(1L, state.reactionRemainingMillis(deadline - 1L));

        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(deadline));
        assertFalse(state.hasActiveReaction(deadline));
        assertNull(state.reactionId(deadline));
    }

    @Test
    public void inactivityRestoresSleepingExactlyThreeMinutesAfterInteraction() {
        CompanionPageState state = openSleepingState();
        long start = 1_000L;
        long reactionEnd = start + CompanionPageState.REACTION_DURATION_MS;
        long inactivityEnd = start + CompanionPageState.INACTIVITY_TIMEOUT_MS;

        assertTrue(state.startReaction("feed_treat", start));
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(reactionEnd));
        assertEquals(
                CompanionPageState.INACTIVITY_TIMEOUT_MS - CompanionPageState.REACTION_DURATION_MS,
                state.nextTransitionRemainingMillis(reactionEnd));
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(inactivityEnd - 1L));
        assertEquals(1L, state.nextTransitionRemainingMillis(inactivityEnd - 1L));
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(inactivityEnd));
        assertEquals(0L, state.nextTransitionRemainingMillis(inactivityEnd));
    }

    @Test
    public void repeatedInteractionCancelsTheFormerInactivityDeadline() {
        CompanionPageState state = openSleepingState();
        long firstStart = 2_000L;
        long secondStart = firstStart + 2_000L;

        assertTrue(state.startReaction("feed_treat", firstStart));
        assertSame(
                CompanionPageState.Pose.IDLE,
                state.displayedPose(firstStart + CompanionPageState.REACTION_DURATION_MS));
        assertTrue(state.startReaction("play", secondStart));

        long formerDeadline = firstStart + CompanionPageState.INACTIVITY_TIMEOUT_MS;
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(formerDeadline));

        long replacementDeadline = secondStart + CompanionPageState.INACTIVITY_TIMEOUT_MS;
        assertSame(CompanionPageState.Pose.IDLE, state.displayedPose(replacementDeadline - 1L));
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(replacementDeadline));
    }

    @Test
    public void sleepingRemainsStableWithoutAnotherScheduledTransition() {
        CompanionPageState state = openSleepingState();

        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(0L));
        assertEquals(0L, state.nextTransitionRemainingMillis(0L));
        assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(600_000L));
        assertEquals(0L, state.nextTransitionRemainingMillis(600_000L));
    }

    @Test
    public void everyOpenCycleUsesSleepingWithoutSessionVariation() {
        CompanionPageState state = new CompanionPageState();

        for (int cycle = 0; cycle < 5; cycle++) {
            state.open();
            assertSame(CompanionPageState.Pose.SLEEPING, state.sessionPose());
            assertSame(CompanionPageState.Pose.SLEEPING, state.displayedPose(cycle));
            state.close();
        }
    }

    @Test
    public void closedPageCannotStartReaction() {
        CompanionPageState state = new CompanionPageState();

        assertFalse(state.startReaction("feed_treat", 700L));
        assertFalse(state.startReaction("play", 700L));
        assertFalse(state.startReaction("cuddle", 700L));
        assertNull(state.displayedPose(701L));
    }

    private static CompanionPageState openSleepingState() {
        CompanionPageState state = new CompanionPageState();
        state.open();
        return state;
    }
}

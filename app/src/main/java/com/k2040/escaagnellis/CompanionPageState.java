package com.k2040.escaagnellis;

/** UI-only state for one open companion-page session. No companion data is stored here. */
final class CompanionPageState {
    static final long REACTION_DURATION_MS = 950L;
    static final long INACTIVITY_TIMEOUT_MS = 180_000L;

    enum Pose {
        EATING,
        CUDDLE,
        HAPPY,
        IDLE,
        SLEEPING
    }

    private static final Pose DEFAULT_SESSION_POSE = Pose.SLEEPING;

    private boolean open;
    private Pose sessionPose;
    private Pose reactionPose;
    private String reservedInteractionId;
    private long reservedInteractionStartedMs;
    private String reactionId;
    private long reactionStartedMs;
    private long reactionUntilMs;
    private long inactivityUntilMs;

    void open() {
        if (open) return;
        open = true;
        sessionPose = DEFAULT_SESSION_POSE;
        clearInteractionState();
        inactivityUntilMs = 0L;
    }

    void close() {
        open = false;
        sessionPose = null;
        clearInteractionState();
        inactivityUntilMs = 0L;
    }

    boolean isOpen() {
        return open;
    }

    Pose sessionPose() {
        return sessionPose;
    }

    Pose displayedPose(long nowMs) {
        if (!open) return null;
        advanceState(nowMs);
        return reactionPose == null ? sessionPose : reactionPose;
    }

    String reactionId(long nowMs) {
        advanceState(nowMs);
        return reactionId;
    }

    boolean hasActiveReaction(long nowMs) {
        advanceState(nowMs);
        return reactionId != null;
    }

    boolean interactionsLocked(long nowMs) {
        if (!open) return false;
        advanceState(nowMs);
        return reservedInteractionId != null || reactionId != null;
    }

    long reactionRemainingMillis(long nowMs) {
        advanceState(nowMs);
        return reactionId == null ? 0L : Math.max(1L, reactionUntilMs - nowMs);
    }

    float reactionProgress(long nowMs) {
        advanceState(nowMs);
        if (reactionId == null) return 0f;
        long elapsedMs = Math.max(0L, nowMs - reactionStartedMs);
        return Math.min(1f, elapsedMs / (float) REACTION_DURATION_MS);
    }

    long nextTransitionRemainingMillis(long nowMs) {
        if (!open) return 0L;
        advanceState(nowMs);
        long nextTransitionMs = 0L;
        if (reactionId != null) nextTransitionMs = reactionUntilMs;
        if (inactivityUntilMs != 0L
                && (nextTransitionMs == 0L || inactivityUntilMs < nextTransitionMs)) {
            nextTransitionMs = inactivityUntilMs;
        }
        return nextTransitionMs == 0L ? 0L : Math.max(1L, nextTransitionMs - nowMs);
    }

    boolean showsTreatBowl(long nowMs) {
        return "feed_treat".equals(reactionId(nowMs));
    }

    boolean showsPlayBall(long nowMs) {
        return "play".equals(reactionId(nowMs));
    }

    boolean showsCuddleHearts(long nowMs) {
        return "cuddle".equals(reactionId(nowMs));
    }

    boolean startReaction(String interactionId, long nowMs) {
        if (!tryBeginInteraction(interactionId, nowMs)) return false;
        return completeInteraction(interactionId);
    }

    boolean tryBeginInteraction(String interactionId, long nowMs) {
        if (!open) return false;
        advanceState(nowMs);
        if (reservedInteractionId != null || reactionId != null) return false;
        if (reactionPoseFor(interactionId) == null) return false;
        reservedInteractionId = interactionId;
        reservedInteractionStartedMs = nowMs;
        return true;
    }

    boolean completeInteraction(String interactionId) {
        if (!open || !interactionIdEquals(reservedInteractionId, interactionId)) return false;
        reactionId = reservedInteractionId;
        reactionPose = reactionPoseFor(reactionId);
        reactionStartedMs = reservedInteractionStartedMs;
        reactionUntilMs = deadlineAfter(reactionStartedMs, REACTION_DURATION_MS);
        inactivityUntilMs = deadlineAfter(reactionStartedMs, INACTIVITY_TIMEOUT_MS);
        clearInteractionReservation();
        return true;
    }

    void cancelInteraction(String interactionId) {
        if (interactionIdEquals(reservedInteractionId, interactionId)) {
            clearInteractionReservation();
        }
    }

    void finishReaction() {
        if (open && reactionId != null) sessionPose = Pose.IDLE;
        clearReactionState();
    }

    private void clearReactionState() {
        reactionId = null;
        reactionPose = null;
        reactionStartedMs = 0L;
        reactionUntilMs = 0L;
    }

    private void clearInteractionReservation() {
        reservedInteractionId = null;
        reservedInteractionStartedMs = 0L;
    }

    private void clearInteractionState() {
        clearInteractionReservation();
        clearReactionState();
    }

    private void advanceState(long nowMs) {
        if (reactionId != null && nowMs >= reactionUntilMs) finishReaction();
        if (inactivityUntilMs != 0L && nowMs >= inactivityUntilMs) {
            clearReactionState();
            sessionPose = Pose.SLEEPING;
            inactivityUntilMs = 0L;
        }
    }

    private static long deadlineAfter(long nowMs, long durationMs) {
        if (nowMs > Long.MAX_VALUE - durationMs) return Long.MAX_VALUE;
        return nowMs + durationMs;
    }

    private static boolean interactionIdEquals(String first, String second) {
        return first != null && first.equals(second);
    }

    private static Pose reactionPoseFor(String interactionId) {
        if ("feed_treat".equals(interactionId)) return Pose.EATING;
        if ("play".equals(interactionId)) return Pose.HAPPY;
        if ("cuddle".equals(interactionId)) return Pose.CUDDLE;
        return null;
    }
}

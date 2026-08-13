package com.k2040.escaagnellis;

import java.time.Instant;
import java.util.Objects;

final class CompanionPresentation {
    static final String POSE_IDLE = "idle";
    static final String POSE_HAPPY = "happy";
    static final String POSE_EATING = "eating";
    static final String MOOD_CONTENT = "content";
    static final String MOOD_HAPPY = "happy";
    static final long REACTION_DURATION_MILLIS = 5_000L;

    private CompanionPresentation() {
    }

    static Snapshot derive(CompanionState state, Instant now) {
        if (state == null) {
            throw new IllegalArgumentException("Missing companion state");
        }
        if (now == null) {
            throw new IllegalArgumentException("Missing presentation timestamp");
        }

        String poseId = POSE_IDLE;
        String moodId = MOOD_CONTENT;
        String reactionId = null;
        long nowMillis = now.toEpochMilli();
        boolean recentReaction = state.enabled
                && state.lastInteractionId != null
                && state.lastInteractionEpochMillis > CompanionState.NO_INTERACTION_TIME
                && nowMillis >= state.lastInteractionEpochMillis
                && nowMillis - state.lastInteractionEpochMillis < REACTION_DURATION_MILLIS;

        if (recentReaction) {
            reactionId = state.lastInteractionId;
            poseId = reactionId.startsWith("feed_") ? POSE_EATING : POSE_HAPPY;
            moodId = MOOD_HAPPY;
        }

        return new Snapshot(
                state.enabled,
                state.companionId,
                state.companionName,
                poseId,
                moodId,
                state.balance,
                reactionId,
                state.reducedAnimation);
    }

    static final class Snapshot {
        final boolean enabled;
        final String companionId;
        final String displayName;
        final String poseId;
        final String moodId;
        final long balance;
        final String reactionId;
        final boolean reducedAnimation;

        Snapshot(
                boolean enabled,
                String companionId,
                String displayName,
                String poseId,
                String moodId,
                long balance,
                String reactionId,
                boolean reducedAnimation) {
            this.enabled = enabled;
            this.companionId = CompanionState.requireStableId(companionId, "companion id");
            this.displayName = CompanionState.normalizeCompanionName(displayName);
            this.poseId = CompanionState.requireStableId(poseId, "pose id");
            this.moodId = CompanionState.requireStableId(moodId, "mood id");
            if (balance < 0L) {
                throw new IllegalArgumentException("Companion balance must not be negative");
            }
            if (reactionId != null) {
                CompanionState.requireStableId(reactionId, "reaction id");
            }
            this.balance = balance;
            this.reactionId = reactionId;
            this.reducedAnimation = reducedAnimation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot)) return false;
            Snapshot that = (Snapshot) other;
            return enabled == that.enabled
                    && balance == that.balance
                    && reducedAnimation == that.reducedAnimation
                    && companionId.equals(that.companionId)
                    && Objects.equals(displayName, that.displayName)
                    && poseId.equals(that.poseId)
                    && moodId.equals(that.moodId)
                    && Objects.equals(reactionId, that.reactionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    enabled,
                    companionId,
                    displayName,
                    poseId,
                    moodId,
                    balance,
                    reactionId,
                    reducedAnimation);
        }
    }
}

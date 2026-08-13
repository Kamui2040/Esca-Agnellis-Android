package com.k2040.escaagnellis;

final class CompanionRewardGracePeriod {
    static final long DURATION_MILLIS = 15_000L;

    private CompanionRewardGracePeriod() {
    }

    static long deadlineAfter(long grantedAtEpochMillis) {
        requireTime(grantedAtEpochMillis);
        return Math.addExact(grantedAtEpochMillis, DURATION_MILLIS);
    }

    static boolean isActive(long deadlineEpochMillis, long nowEpochMillis) {
        requireDeadline(deadlineEpochMillis);
        requireTime(nowEpochMillis);
        long grantedAtEpochMillis = deadlineEpochMillis - DURATION_MILLIS;
        return nowEpochMillis >= grantedAtEpochMillis
                && nowEpochMillis < deadlineEpochMillis;
    }

    static boolean isExpired(long deadlineEpochMillis, long nowEpochMillis) {
        return !isActive(deadlineEpochMillis, nowEpochMillis);
    }

    private static void requireDeadline(long deadlineEpochMillis) {
        if (deadlineEpochMillis < DURATION_MILLIS) {
            throw new IllegalArgumentException("Grace deadline is too early");
        }
    }

    private static void requireTime(long epochMillis) {
        if (epochMillis < 0L) {
            throw new IllegalArgumentException("Epoch time must not be negative");
        }
    }
}

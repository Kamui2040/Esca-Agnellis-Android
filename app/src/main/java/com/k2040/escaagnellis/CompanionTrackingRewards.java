package com.k2040.escaagnellis;

import java.time.LocalDate;

final class CompanionTrackingRewards {
    private CompanionTrackingRewards() {
    }

    static Summary awardNewlySelectedDefaults(
            LocalDate date,
            boolean[] beforeTicks,
            boolean[] afterTicks,
            RewardSink rewardSink) {
        if (date == null) {
            throw new IllegalArgumentException("Missing tracking date");
        }
        requireTickSnapshot(beforeTicks, "before");
        requireTickSnapshot(afterTicks, "after");
        if (rewardSink == null) {
            throw new IllegalArgumentException("Missing companion reward sink");
        }

        int detectedCount = 0;
        int grantedCount = 0;
        int notGrantedCount = 0;
        int failureCount = 0;

        for (int position = 0; position < PyramidScheme.TILE_COUNT; position++) {
            if (beforeTicks[position] || !afterTicks[position]) {
                continue;
            }

            detectedCount++;
            try {
                CompanionRepository.MutationResult result =
                        rewardSink.rewardDefaultPosition(date, position);
                if (result == null) {
                    failureCount++;
                } else if (result.status == CompanionRepository.MutationStatus.APPLIED
                        && result.economyOutcome == CompanionEconomy.Outcome.REWARD_GRANTED) {
                    grantedCount++;
                } else if (result.status == CompanionRepository.MutationStatus.INDETERMINATE
                        || result.status == CompanionRepository.MutationStatus.APPLIED) {
                    failureCount++;
                } else {
                    notGrantedCount++;
                }
            } catch (RuntimeException ignored) {
                failureCount++;
            }
        }

        return new Summary(
                detectedCount,
                grantedCount,
                notGrantedCount,
                failureCount);
    }

    private static void requireTickSnapshot(boolean[] ticks, String label) {
        if (ticks == null || ticks.length != PyramidScheme.TILE_COUNT) {
            throw new IllegalArgumentException("Invalid " + label + " tracking snapshot");
        }
    }

    interface RewardSink {
        CompanionRepository.MutationResult rewardDefaultPosition(LocalDate date, int position);
    }

    static final class Summary {
        final int detectedCount;
        final int grantedCount;
        final int notGrantedCount;
        final int failureCount;

        Summary(
                int detectedCount,
                int grantedCount,
                int notGrantedCount,
                int failureCount) {
            this.detectedCount = detectedCount;
            this.grantedCount = grantedCount;
            this.notGrantedCount = notGrantedCount;
            this.failureCount = failureCount;
        }

        boolean hasFailures() {
            return failureCount > 0;
        }
    }
}

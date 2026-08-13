package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompanionTrackingRewardsTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);

    @Test
    public void awardsOnlyFalseToTrueDefaultPositionsInOrder() {
        boolean[] before = new boolean[PyramidScheme.TILE_COUNT];
        boolean[] after = new boolean[PyramidScheme.TILE_COUNT];
        before[3] = true;
        before[5] = true;
        after[0] = true;
        after[5] = true;
        after[16] = true;
        List<Integer> awardedPositions = new ArrayList<>();

        CompanionTrackingRewards.Summary summary =
                CompanionTrackingRewards.awardNewlySelectedDefaults(
                        DATE,
                        before,
                        after,
                        (date, position) -> {
                            assertEquals(DATE, date);
                            awardedPositions.add(position);
                            return appliedReward();
                        });

        assertEquals(Arrays.asList(0, 16), awardedPositions);
        assertEquals(2, summary.detectedCount);
        assertEquals(2, summary.grantedCount);
        assertEquals(0, summary.notGrantedCount);
        assertEquals(0, summary.failureCount);
        assertFalse(summary.hasFailures());
    }

    @Test
    public void removalsAndUnchangedTicksDoNotReachRewardSink() {
        boolean[] before = new boolean[PyramidScheme.TILE_COUNT];
        boolean[] after = new boolean[PyramidScheme.TILE_COUNT];
        before[0] = true;
        before[1] = true;
        after[1] = true;

        CompanionTrackingRewards.Summary summary =
                CompanionTrackingRewards.awardNewlySelectedDefaults(
                        DATE,
                        before,
                        after,
                        (date, position) -> {
                            throw new AssertionError("Reward sink must not be called");
                        });

        assertEquals(0, summary.detectedCount);
        assertEquals(0, summary.grantedCount);
        assertEquals(0, summary.notGrantedCount);
        assertEquals(0, summary.failureCount);
    }

    @Test
    public void disabledDuplicateAndUnavailableResultsRemainNonBlocking() {
        boolean[] before = new boolean[PyramidScheme.TILE_COUNT];
        boolean[] after = new boolean[PyramidScheme.TILE_COUNT];
        after[0] = true;
        after[1] = true;
        after[2] = true;

        CompanionTrackingRewards.Summary summary =
                CompanionTrackingRewards.awardNewlySelectedDefaults(
                        DATE,
                        before,
                        after,
                        (date, position) -> {
                            if (position == 0) {
                                return CompanionRepository.MutationResult.noChange(
                                        CompanionState.disabledDefault(),
                                        CompanionEconomy.Outcome.DISABLED,
                                        CompanionRepository.LoadStatus.EMPTY);
                            }
                            if (position == 1) {
                                return CompanionRepository.MutationResult.noChange(
                                        CompanionState.disabledDefault().withEnabled(true),
                                        CompanionEconomy.Outcome.ALREADY_REWARDED,
                                        CompanionRepository.LoadStatus.LOADED);
                            }
                            return new CompanionRepository.MutationResult(
                                    CompanionRepository.MutationStatus.UNAVAILABLE,
                                    CompanionState.disabledDefault(),
                                    null,
                                    CompanionRepository.LoadStatus.CORRUPT);
                        });

        assertEquals(3, summary.detectedCount);
        assertEquals(0, summary.grantedCount);
        assertEquals(3, summary.notGrantedCount);
        assertEquals(0, summary.failureCount);
        assertFalse(summary.hasFailures());
    }

    @Test
    public void indeterminateWriteIsReportedAsFailureWithoutThrowing() {
        boolean[] before = new boolean[PyramidScheme.TILE_COUNT];
        boolean[] after = new boolean[PyramidScheme.TILE_COUNT];
        after[0] = true;

        CompanionTrackingRewards.Summary summary =
                CompanionTrackingRewards.awardNewlySelectedDefaults(
                        DATE,
                        before,
                        after,
                        (date, position) -> new CompanionRepository.MutationResult(
                                CompanionRepository.MutationStatus.INDETERMINATE,
                                CompanionState.disabledDefault(),
                                CompanionEconomy.Outcome.REWARD_GRANTED,
                                null));

        assertEquals(1, summary.detectedCount);
        assertEquals(0, summary.grantedCount);
        assertEquals(0, summary.notGrantedCount);
        assertEquals(1, summary.failureCount);
        assertTrue(summary.hasFailures());
    }

    @Test
    public void sinkFailureIsContainedAndLaterRewardsContinue() {
        boolean[] before = new boolean[PyramidScheme.TILE_COUNT];
        boolean[] after = new boolean[PyramidScheme.TILE_COUNT];
        after[0] = true;
        after[16] = true;

        CompanionTrackingRewards.Summary summary =
                CompanionTrackingRewards.awardNewlySelectedDefaults(
                        DATE,
                        before,
                        after,
                        (date, position) -> {
                            if (position == 0) {
                                throw new IllegalStateException("simulated storage failure");
                            }
                            return appliedReward();
                        });

        assertEquals(2, summary.detectedCount);
        assertEquals(1, summary.grantedCount);
        assertEquals(0, summary.notGrantedCount);
        assertEquals(1, summary.failureCount);
        assertTrue(summary.hasFailures());
    }

    @Test
    public void rejectsInvalidSnapshotsBeforeCallingRewardSink() {
        CompanionTrackingRewards.RewardSink sink = (date, position) -> appliedReward();

        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionTrackingRewards.awardNewlySelectedDefaults(
                        null,
                        new boolean[PyramidScheme.TILE_COUNT],
                        new boolean[PyramidScheme.TILE_COUNT],
                        sink));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionTrackingRewards.awardNewlySelectedDefaults(
                        DATE,
                        new boolean[PyramidScheme.TILE_COUNT - 1],
                        new boolean[PyramidScheme.TILE_COUNT],
                        sink));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionTrackingRewards.awardNewlySelectedDefaults(
                        DATE,
                        new boolean[PyramidScheme.TILE_COUNT],
                        new boolean[PyramidScheme.TILE_COUNT],
                        null));
    }

    private static CompanionRepository.MutationResult appliedReward() {
        return new CompanionRepository.MutationResult(
                CompanionRepository.MutationStatus.APPLIED,
                CompanionState.disabledDefault().withEnabled(true),
                CompanionEconomy.Outcome.REWARD_GRANTED,
                CompanionRepository.LoadStatus.LOADED);
    }
}

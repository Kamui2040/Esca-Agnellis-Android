package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CompanionRepositoryTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 2);

    @Test
    public void emptyStoreLoadsDisabledAndCanOptIn() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);

        CompanionRepository.LoadResult empty = repository.load(1_000L);
        assertEquals(CompanionRepository.LoadStatus.EMPTY, empty.status);
        assertFalse(empty.state.enabled);

        CompanionRepository.MutationResult enabled = repository.setEnabled(true, 1_000L);
        assertEquals(CompanionRepository.MutationStatus.APPLIED, enabled.status);
        assertTrue(enabled.state.enabled);
        assertEquals(CompanionRepository.LoadStatus.LOADED, repository.load(1_000L).status);
    }

    @Test
    public void companionNamePersistsAcrossRepositoryReloads() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);

        CompanionRepository.MutationResult saved = repository.setCompanionName(
                "  Luna   Star  ");
        assertEquals(CompanionRepository.MutationStatus.APPLIED, saved.status);
        assertEquals("Luna Star", saved.state.companionName);

        CompanionRepository recreated = repository(store);
        assertEquals("Luna Star", recreated.load(1_000L).state.companionName);

        CompanionRepository.MutationResult cleared = recreated.setCompanionName("   ");
        assertEquals(CompanionRepository.MutationStatus.APPLIED, cleared.status);
        assertNull(cleared.state.companionName);
        assertNull(repository(store).load(1_000L).state.companionName);
    }

    @Test
    public void rewardAndDeadlinePersistAcrossRepositoryReload() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);
        repository.setEnabled(true, 1_000L);

        CompanionRepository.MutationResult first = repository.rewardDefaultPosition(
                DATE,
                0,
                1_000L);
        CompanionRepository.MutationResult repeated = repository.rewardDefaultPosition(
                DATE,
                0,
                2_000L);

        assertEquals(CompanionRepository.MutationStatus.APPLIED, first.status);
        assertEquals(CompanionEconomy.Outcome.REWARD_GRANTED, first.economyOutcome);
        assertEquals(1L, first.state.balance);
        assertEquals(Long.valueOf(16_000L), first.state.rewardGraceDeadline(DATE, 0));
        assertEquals(CompanionRepository.MutationStatus.NO_CHANGE, repeated.status);
        assertEquals(CompanionEconomy.Outcome.ALREADY_REWARDED, repeated.economyOutcome);

        CompanionRepository recreated = repository(store);
        CompanionState recreatedState = recreated.load(5_000L).state;
        assertEquals(1L, recreatedState.balance);
        assertTrue(recreatedState.hasReward(DATE, 0));
        assertTrue(recreatedState.hasActiveRewardGrace(DATE, 0, 5_000L));
    }

    @Test
    public void simultaneousDeadlinesRemainIndependentAcrossReload() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);
        repository.setEnabled(true, 1_000L);
        repository.rewardDefaultPosition(DATE, 0, 1_000L);
        repository.rewardDefaultPosition(DATE, 1, 10_000L);

        CompanionRepository recreated = repository(store);
        CompanionState beforeFirstExpiry = recreated.load(15_999L).state;
        assertEquals(2, beforeFirstExpiry.activeRewardGraceCount(15_999L));
        assertTrue(beforeFirstExpiry.hasActiveRewardGrace(DATE, 0, 15_999L));
        assertTrue(beforeFirstExpiry.hasActiveRewardGrace(DATE, 1, 15_999L));

        CompanionState afterFirstExpiry = recreated.load(16_000L).state;
        assertEquals(1, afterFirstExpiry.activeRewardGraceCount(16_000L));
        assertNull(afterFirstExpiry.rewardGraceDeadline(DATE, 0));
        assertTrue(afterFirstExpiry.hasActiveRewardGrace(DATE, 1, 16_000L));

        CompanionRepository.MutationResult expired = recreated.correctDefaultPosition(
                DATE,
                0,
                16_000L);
        assertEquals(CompanionEconomy.Outcome.REWARD_GRACE_EXPIRED, expired.economyOutcome);
        assertTrue(expired.state.hasActiveRewardGrace(DATE, 1, 16_000L));

        CompanionState persisted = repository(store).load(16_000L).state;
        assertNull(persisted.rewardGraceDeadline(DATE, 0));
        assertTrue(persisted.hasActiveRewardGrace(DATE, 1, 16_000L));
    }

    @Test
    public void correctionBeforeDeadlineWorksAfterRepositoryReload() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);
        repository.setEnabled(true, 1_000L);
        repository.rewardDefaultPosition(DATE, 0, 1_000L);

        CompanionRepository recreated = repository(store);
        CompanionRepository.MutationResult corrected = recreated.correctDefaultPosition(
                DATE,
                0,
                5_000L);

        assertEquals(CompanionRepository.MutationStatus.APPLIED, corrected.status);
        assertEquals(CompanionEconomy.Outcome.REWARD_REVOKED, corrected.economyOutcome);
        assertEquals(0L, corrected.state.balance);
        assertFalse(corrected.state.hasReward(DATE, 0));
        assertNull(corrected.state.rewardGraceDeadline(DATE, 0));

        CompanionState reloaded = repository(store).load(5_000L).state;
        assertEquals(0L, reloaded.balance);
        assertFalse(reloaded.hasReward(DATE, 0));
    }

    @Test
    public void correctedPositionCanBeGrantedAgainWithFreshDeadline() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);
        repository.setEnabled(true, 1_000L);
        repository.rewardDefaultPosition(DATE, 0, 1_000L);
        repository.correctDefaultPosition(DATE, 0, 5_000L);

        CompanionRepository.MutationResult grantedAgain =
                repository.rewardDefaultPosition(DATE, 0, 6_000L);

        assertEquals(CompanionRepository.MutationStatus.APPLIED, grantedAgain.status);
        assertEquals(CompanionEconomy.Outcome.REWARD_GRANTED, grantedAgain.economyOutcome);
        assertEquals(1L, grantedAgain.state.balance);
        assertTrue(grantedAgain.state.hasReward(DATE, 0));
        assertEquals(
                Long.valueOf(21_000L),
                grantedAgain.state.rewardGraceDeadline(DATE, 0));
    }

    @Test
    public void correctionAfterDeadlineKeepsRewardAndPersistsExpiredCleanup() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);
        repository.setEnabled(true, 1_000L);
        repository.rewardDefaultPosition(DATE, 0, 1_000L);

        CompanionRepository.MutationResult corrected = repository(store)
                .correctDefaultPosition(DATE, 0, 16_000L);

        assertEquals(CompanionRepository.MutationStatus.APPLIED, corrected.status);
        assertEquals(
                CompanionEconomy.Outcome.REWARD_GRACE_EXPIRED,
                corrected.economyOutcome);
        assertEquals(1L, corrected.state.balance);
        assertTrue(corrected.state.hasReward(DATE, 0));
        assertNull(corrected.state.rewardGraceDeadline(DATE, 0));
        assertTrue(((Set<?>) store.values.get(
                CompanionStateCodec.KEY_REWARD_GRACE_DEADLINES)).isEmpty());

        CompanionState reloaded = repository(store).load(20_000L).state;
        assertEquals(1L, reloaded.balance);
        assertTrue(reloaded.hasReward(DATE, 0));
    }

    @Test
    public void legacySchemaMigratesWithoutResettingCompanionData() {
        FakeStore store = new FakeStore();
        CompanionState legacyState = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReducedAnimation(true)
                .withReward(DATE, 0, 1L)
                .withReward(DATE.plusDays(1), 1, 1L);
        store.values.putAll(legacyValues(legacyState));

        CompanionRepository repository = repository(store);
        CompanionRepository.LoadResult loaded = repository.load(1_000L);

        assertEquals(CompanionRepository.LoadStatus.LOADED, loaded.status);
        assertTrue(loaded.migrationRequired);
        assertTrue(loaded.state.enabled);
        assertTrue(loaded.state.reducedAnimation);
        assertEquals(2L, loaded.state.balance);
        assertTrue(loaded.state.hasReward(DATE, 0));
        assertTrue(loaded.state.hasReward(DATE.plusDays(1), 1));
        assertTrue(loaded.state.rewardGraceDeadlines.isEmpty());

        CompanionRepository.MutationResult migrationWrite =
                repository.setEnabled(true, 1_000L);
        assertEquals(CompanionRepository.MutationStatus.APPLIED, migrationWrite.status);
        assertEquals(
                CompanionState.STORAGE_SCHEMA_VERSION,
                store.values.get(CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION));
        assertTrue(store.values.containsKey(CompanionStateCodec.KEY_REWARD_GRACE_DEADLINES));

        CompanionRepository.LoadResult migrated = repository(store).load(1_000L);
        assertFalse(migrated.migrationRequired);
        assertEquals(2L, migrated.state.balance);
        assertEquals(legacyState.rewardLedger, migrated.state.rewardLedger);
    }

    @Test
    public void backupRestorePreservesExistingLocalNameWhenBackupHasNone() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);
        repository.setCompanionName("Mila");
        repository.setEnabled(true, 1_000L);

        CompanionState backupState = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReducedAnimation(true)
                .withReward(DATE, 0, 1L);
        Map<String, Object> backupDocument = CompanionBackupCodec.encode(backupState);

        CompanionBackupManager.RestoreResult restored =
                new CompanionBackupManager(repository).restoreBackup(backupDocument, 2_000L);

        assertEquals(CompanionBackupManager.RestoreStatus.APPLIED, restored.status);
        assertEquals("Mila", restored.state.companionName);

        CompanionState reloaded = repository(store).load(2_000L).state;
        assertEquals("Mila", reloaded.companionName);
        assertTrue(reloaded.enabled);
        assertEquals(1L, reloaded.balance);
    }

    @Test
    public void activeRewardBalanceIsReservedSoCorrectionCannotGoNegative() {
        FakeStore store = new FakeStore();
        CompanionState settled = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(DATE.minusDays(1), 0, 1L);
        store.values.putAll(CompanionStateCodec.encode(settled));
        CompanionRepository repository = repository(store);
        repository.rewardDefaultPosition(DATE, 0, 1_000L);

        CompanionRepository.MutationResult purchase = repository.purchaseInteraction(
                "feed_apple",
                1L,
                Instant.ofEpochMilli(2_000L));
        assertEquals(CompanionRepository.MutationStatus.APPLIED, purchase.status);
        assertEquals(CompanionEconomy.Outcome.PURCHASE_COMPLETED, purchase.economyOutcome);
        assertEquals(1L, purchase.state.balance);

        CompanionRepository.MutationResult corrected = repository.correctDefaultPosition(
                DATE,
                0,
                3_000L);

        assertEquals(CompanionRepository.MutationStatus.APPLIED, corrected.status);
        assertEquals(CompanionEconomy.Outcome.REWARD_REVOKED, corrected.economyOutcome);
        assertEquals(0L, corrected.state.balance);
        assertFalse(corrected.state.hasReward(DATE, 0));
        assertTrue(corrected.state.hasReward(DATE.minusDays(1), 0));
        assertNull(corrected.state.rewardGraceDeadline(DATE, 0));
        assertEquals(0L, repository(store).load(3_000L).state.balance);
    }

    @Test
    public void purchaseCompletesAndPersistsInteractionDetails() {
        FakeStore store = new FakeStore();
        CompanionState funded = CompanionState.disabledDefault().withEnabled(true);
        for (int position = 0; position < 6; position++) {
            funded = funded.withReward(DATE, position, 1L);
        }
        store.values.putAll(CompanionStateCodec.encode(funded));
        CompanionRepository repository = repository(store);

        CompanionRepository.MutationResult purchase = repository.purchaseInteraction(
                "cuddle",
                6L,
                Instant.ofEpochMilli(20_000L));

        assertEquals(CompanionRepository.MutationStatus.APPLIED, purchase.status);
        assertEquals(CompanionEconomy.Outcome.PURCHASE_COMPLETED, purchase.economyOutcome);
        assertEquals(0L, purchase.state.balance);
        assertEquals("cuddle", purchase.state.lastInteractionId);
        assertEquals(20_000L, purchase.state.lastInteractionEpochMillis);

        CompanionState reloaded = repository(store).load(20_000L).state;
        assertEquals(0L, reloaded.balance);
        assertEquals("cuddle", reloaded.lastInteractionId);
        assertEquals(20_000L, reloaded.lastInteractionEpochMillis);
    }

    @Test
    public void purchaseFailsWhenBalanceIsInsufficient() {
        FakeStore store = new FakeStore();
        CompanionState funded = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(DATE, 0, 1L)
                .withReward(DATE, 1, 1L);
        store.values.putAll(CompanionStateCodec.encode(funded));
        CompanionRepository repository = repository(store);

        CompanionRepository.MutationResult purchase = repository.purchaseInteraction(
                "play",
                4L,
                Instant.ofEpochMilli(20_000L));

        assertEquals(CompanionRepository.MutationStatus.NO_CHANGE, purchase.status);
        assertEquals(CompanionEconomy.Outcome.INSUFFICIENT_BALANCE, purchase.economyOutcome);
        assertEquals(2L, purchase.state.balance);
        assertNull(purchase.state.lastInteractionId);
        assertEquals(CompanionState.NO_INTERACTION_TIME, purchase.state.lastInteractionEpochMillis);
    }

    @Test
    public void purchaseFailsClosedWhileCompanionIsDisabled() {
        FakeStore store = new FakeStore();
        CompanionRepository repository = repository(store);

        CompanionRepository.MutationResult purchase = repository.purchaseInteraction(
                "feed_treat",
                2L,
                Instant.ofEpochMilli(20_000L));

        assertEquals(CompanionRepository.MutationStatus.NO_CHANGE, purchase.status);
        assertEquals(CompanionEconomy.Outcome.DISABLED, purchase.economyOutcome);
        assertFalse(purchase.state.enabled);
        assertEquals(0L, purchase.state.balance);
    }

    @Test
    public void activeRewardBalanceRemainsRetainedDuringPurchaseChecks() {
        FakeStore store = new FakeStore();
        CompanionState settled = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(DATE.minusDays(1), 1, 1L);
        store.values.putAll(CompanionStateCodec.encode(settled));
        CompanionRepository repository = repository(store);
        repository.rewardDefaultPosition(DATE, 0, 10_000L);

        CompanionRepository.MutationResult purchase = repository.purchaseInteraction(
                "feed_treat",
                2L,
                Instant.ofEpochMilli(20_000L));

        assertEquals(CompanionRepository.MutationStatus.NO_CHANGE, purchase.status);
        assertEquals(CompanionEconomy.Outcome.INSUFFICIENT_BALANCE, purchase.economyOutcome);
        assertEquals(2L, purchase.state.balance);
        assertTrue(purchase.state.hasActiveRewardGrace(DATE, 0, 20_000L));
        assertTrue(purchase.state.hasReward(DATE.minusDays(1), 1));
    }

    @Test
    public void corruptAndUnsupportedStateFailClosed() {
        FakeStore corruptStore = new FakeStore();
        corruptStore.values.put(
                CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION,
                CompanionState.LEGACY_STORAGE_SCHEMA_VERSION);
        CompanionRepository corrupt = repository(corruptStore);
        CompanionRepository.LoadResult corruptLoad = corrupt.load(1_000L);
        assertEquals(CompanionRepository.LoadStatus.CORRUPT, corruptLoad.status);
        assertFalse(corruptLoad.state.enabled);
        assertEquals(
                CompanionRepository.MutationStatus.UNAVAILABLE,
                corrupt.setEnabled(true, 1_000L).status);

        FakeStore unsupportedStore = new FakeStore();
        unsupportedStore.values.putAll(CompanionStateCodec.encode(CompanionState.disabledDefault()));
        unsupportedStore.values.put(CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION, 99);
        CompanionRepository unsupported = repository(unsupportedStore);
        assertEquals(
                CompanionRepository.LoadStatus.UNSUPPORTED_SCHEMA,
                unsupported.load(1_000L).status);
    }

    @Test
    public void failedWritesReportRollbackOrIndeterminateWithoutClaimingTarget() {
        FakeStore rollbackStore = new FakeStore();
        rollbackStore.nextOutcome = CompanionStateStore.WriteOutcome.ORIGINAL_RESTORED;
        CompanionRepository rollback = repository(rollbackStore);

        CompanionRepository.MutationResult rolledBack = rollback.setEnabled(true, 1_000L);
        assertEquals(CompanionRepository.MutationStatus.ORIGINAL_RESTORED, rolledBack.status);
        assertFalse(rolledBack.state.enabled);
        assertTrue(rollbackStore.values.isEmpty());

        FakeStore indeterminateStore = new FakeStore();
        indeterminateStore.nextOutcome = CompanionStateStore.WriteOutcome.INDETERMINATE;
        CompanionRepository indeterminate = repository(indeterminateStore);

        CompanionRepository.MutationResult uncertain =
                indeterminate.setEnabled(true, 1_000L);
        assertEquals(CompanionRepository.MutationStatus.INDETERMINATE, uncertain.status);
        assertFalse(uncertain.state.enabled);
        assertNull(uncertain.loadStatus);
    }

    private static CompanionRepository repository(FakeStore store) {
        return new CompanionRepository(store, CompanionEconomy.defaultEconomy());
    }

    private static Map<String, Object> legacyValues(CompanionState state) {
        Map<String, Object> legacy = new LinkedHashMap<>(CompanionStateCodec.encode(state));
        legacy.put(
                CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION,
                CompanionState.LEGACY_STORAGE_SCHEMA_VERSION);
        legacy.remove(CompanionStateCodec.KEY_REWARD_GRACE_DEADLINES);
        return legacy;
    }

    private static final class FakeStore implements CompanionStateStore {
        final Map<String, Object> values = new LinkedHashMap<>();
        WriteOutcome nextOutcome = WriteOutcome.TARGET_APPLIED;

        @Override
        public Map<String, ?> readAll() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        @Override
        public WriteOutcome replaceAll(Map<String, ?> target) {
            WriteOutcome outcome = nextOutcome;
            nextOutcome = WriteOutcome.TARGET_APPLIED;
            if (outcome == WriteOutcome.TARGET_APPLIED) {
                values.clear();
                for (Map.Entry<String, ?> entry : target.entrySet()) {
                    values.put(entry.getKey(), entry.getValue());
                }
            }
            return outcome;
        }
    }
}

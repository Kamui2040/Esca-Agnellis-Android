package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompanionBackupManagerTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 4);

    @Test
    public void emptyStoreExportsDisabledCompanionDocument() {
        FakeStore store = new FakeStore();
        CompanionBackupManager.ExportResult result =
                manager(store).exportBackup(1_000L);

        assertEquals(
                CompanionBackupManager.ExportStatus.EXPORTED,
                result.status);
        assertNotNull(result.document);
        assertNull(result.loadStatus);
        assertEquals(
                CompanionState.disabledDefault(),
                CompanionBackupCodec.decode(result.document));
        assertEquals(0, store.replaceCalls);
    }

    @Test
    public void corruptOrUnsupportedStoredStateCannotBeExported() {
        FakeStore corrupt = new FakeStore();
        corrupt.values.put(CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION, 2);
        CompanionBackupManager.ExportResult corruptResult =
                manager(corrupt).exportBackup(1_000L);
        assertEquals(
                CompanionBackupManager.ExportStatus.UNAVAILABLE,
                corruptResult.status);
        assertEquals(
                CompanionRepository.LoadStatus.CORRUPT,
                corruptResult.loadStatus);

        FakeStore unsupported = new FakeStore();
        unsupported.values.putAll(
                CompanionStateCodec.encode(CompanionState.disabledDefault()));
        unsupported.values.put(
                CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION,
                99);
        CompanionBackupManager.ExportResult unsupportedResult =
                manager(unsupported).exportBackup(1_000L);
        assertEquals(
                CompanionBackupManager.ExportStatus.UNAVAILABLE,
                unsupportedResult.status);
        assertEquals(
                CompanionRepository.LoadStatus.UNSUPPORTED_SCHEMA,
                unsupportedResult.loadStatus);
    }

    @Test
    public void validBackupRestoresAllStateThroughRepository() {
        FakeStore store = new FakeStore();
        CompanionState target = stateWithActiveDeadline();
        Map<String, Object> document = CompanionBackupCodec.encode(target);

        CompanionBackupManager.RestoreResult result =
                manager(store).restoreBackup(document, 5_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.APPLIED,
                result.status);
        assertEquals(target, result.state);
        assertEquals(1, store.replaceCalls);

        CompanionState loaded = repository(store).load(5_000L).state;
        assertEquals(target, loaded);
        assertTrue(loaded.hasActiveRewardGrace(DATE, 0, 5_000L));
    }

    @Test
    public void validBackupCanReplaceCorruptCurrentCompanionState() {
        FakeStore store = new FakeStore();
        store.values.put(CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION, 99);
        store.values.put("unknown", "corrupt");
        CompanionState target = CompanionState.disabledDefault().withEnabled(true);

        CompanionBackupManager.RestoreResult result = manager(store).restoreBackup(
                CompanionBackupCodec.encode(target),
                1_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.APPLIED,
                result.status);
        assertEquals(target, repository(store).load(1_000L).state);
        assertEquals(1, store.replaceCalls);
    }

    @Test
    public void decodeFailuresNeverReachCompanionStorage() {
        FakeStore store = new FakeStore();
        CompanionBackupManager manager = manager(store);

        Map<String, Object> wrongFormat = new LinkedHashMap<>();
        wrongFormat.put(
                CompanionBackupCodec.KEY_SCHEMA_NAME,
                "esca-agnellis-export");
        assertEquals(
                CompanionBackupManager.RestoreStatus.WRONG_FORMAT,
                manager.restoreBackup(wrongFormat, 1_000L).status);

        Map<String, Object> unsupported =
                mutableCopy(CompanionBackupCodec.encode(
                        CompanionState.disabledDefault()));
        unsupported.put(CompanionBackupCodec.KEY_SCHEMA_VERSION, 99);
        assertEquals(
                CompanionBackupManager.RestoreStatus.UNSUPPORTED_SCHEMA,
                manager.restoreBackup(unsupported, 1_000L).status);

        Map<String, Object> invalid =
                mutableCopy(CompanionBackupCodec.encode(
                        CompanionState.disabledDefault()));
        invalid.remove(CompanionBackupCodec.KEY_STATE);
        assertEquals(
                CompanionBackupManager.RestoreStatus.INVALID,
                manager.restoreBackup(invalid, 1_000L).status);

        assertEquals(0, store.replaceCalls);
        assertTrue(store.values.isEmpty());
    }

    @Test
    public void identicalBackupDoesNotRewriteStorage() {
        FakeStore store = new FakeStore();
        CompanionState state = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(DATE, 0, 1L);
        store.values.putAll(CompanionStateCodec.encode(state));

        CompanionBackupManager.RestoreResult result = manager(store).restoreBackup(
                CompanionBackupCodec.encode(state),
                1_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.NO_CHANGE,
                result.status);
        assertEquals(state, result.state);
        assertEquals(0, store.replaceCalls);
    }

    @Test
    public void expiredDeadlinesAreRemovedBeforeBackupRestoreWrite() {
        FakeStore store = new FakeStore();
        CompanionState target = stateWithActiveDeadline();

        CompanionBackupManager.RestoreResult result = manager(store).restoreBackup(
                CompanionBackupCodec.encode(target),
                16_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.APPLIED,
                result.status);
        assertNull(result.state.rewardGraceDeadline(DATE, 0));
        assertTrue(result.state.hasReward(DATE, 0));
        assertEquals(1L, result.state.balance);

        CompanionState loaded = repository(store).load(16_000L).state;
        assertNull(loaded.rewardGraceDeadline(DATE, 0));
        assertTrue(loaded.hasReward(DATE, 0));
    }

    @Test
    public void rollbackAndIndeterminateOutcomesAreReportedWithoutClaimingTarget() {
        CompanionState original = CompanionState.disabledDefault();
        CompanionState target = original.withEnabled(true);

        FakeStore rollbackStore = new FakeStore();
        rollbackStore.values.putAll(CompanionStateCodec.encode(original));
        rollbackStore.nextOutcome =
                CompanionStateStore.WriteOutcome.ORIGINAL_RESTORED;
        Map<String, Object> rollbackBefore =
                new LinkedHashMap<>(rollbackStore.values);

        CompanionBackupManager.RestoreResult rollback =
                manager(rollbackStore).restoreBackup(
                        CompanionBackupCodec.encode(target),
                        1_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.ORIGINAL_RESTORED,
                rollback.status);
        assertNull(rollback.state);
        assertEquals(rollbackBefore, rollbackStore.values);

        FakeStore indeterminateStore = new FakeStore();
        indeterminateStore.values.putAll(CompanionStateCodec.encode(original));
        indeterminateStore.nextOutcome =
                CompanionStateStore.WriteOutcome.INDETERMINATE;

        CompanionBackupManager.RestoreResult indeterminate =
                manager(indeterminateStore).restoreBackup(
                        CompanionBackupCodec.encode(target),
                        1_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.INDETERMINATE,
                indeterminate.status);
        assertNull(indeterminate.state);
    }

    @Test
    public void storageReadFailureIsReportedWithoutAttemptingWrite() {
        FakeStore store = new FakeStore();
        store.throwOnRead = true;

        CompanionBackupManager.RestoreResult result = manager(store).restoreBackup(
                CompanionBackupCodec.encode(
                        CompanionState.disabledDefault().withEnabled(true)),
                1_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.STORAGE_UNAVAILABLE,
                result.status);
        assertNull(result.state);
        assertEquals(0, store.replaceCalls);
    }

    @Test
    public void storageWriteExceptionIsIndeterminate() {
        FakeStore store = new FakeStore();
        store.throwOnWrite = true;

        CompanionBackupManager.RestoreResult result = manager(store).restoreBackup(
                CompanionBackupCodec.encode(
                        CompanionState.disabledDefault().withEnabled(true)),
                1_000L);

        assertEquals(
                CompanionBackupManager.RestoreStatus.INDETERMINATE,
                result.status);
        assertNull(result.state);
        assertEquals(1, store.replaceCalls);
    }

    private static CompanionState stateWithActiveDeadline() {
        return CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(DATE, 0, 1L)
                .withRewardGraceDeadline(DATE, 0, 16_000L);
    }

    private static CompanionBackupManager manager(FakeStore store) {
        return new CompanionBackupManager(repository(store));
    }

    private static CompanionRepository repository(FakeStore store) {
        return new CompanionRepository(
                store,
                CompanionEconomy.defaultEconomy());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableCopy(Map<String, Object> source) {
        return (Map<String, Object>) deepCopy(source);
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put((String) entry.getKey(), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?>) {
            List<Object> copy = new ArrayList<>();
            for (Object entry : (List<?>) value) {
                copy.add(deepCopy(entry));
            }
            return copy;
        }
        if (value instanceof Set<?>) {
            return new LinkedHashSet<>((Set<?>) value);
        }
        return value;
    }

    private static final class FakeStore implements CompanionStateStore {
        final Map<String, Object> values = new LinkedHashMap<>();
        WriteOutcome nextOutcome = WriteOutcome.TARGET_APPLIED;
        boolean throwOnRead;
        boolean throwOnWrite;
        int replaceCalls;

        @Override
        public Map<String, ?> readAll() {
            if (throwOnRead) {
                throw new IllegalStateException("read failed");
            }
            return Collections.unmodifiableMap(copyValues(values));
        }

        @Override
        public WriteOutcome replaceAll(Map<String, ?> target) {
            replaceCalls++;
            if (throwOnWrite) {
                throw new IllegalStateException("write failed");
            }

            WriteOutcome outcome = nextOutcome;
            nextOutcome = WriteOutcome.TARGET_APPLIED;
            if (outcome == WriteOutcome.TARGET_APPLIED) {
                values.clear();
                values.putAll(copyValues(target));
            }
            return outcome;
        }

        private static Map<String, Object> copyValues(Map<String, ?> source) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : source.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Set<?>) {
                    value = new LinkedHashSet<>((Set<?>) value);
                }
                copy.put(entry.getKey(), value);
            }
            return copy;
        }
    }
}

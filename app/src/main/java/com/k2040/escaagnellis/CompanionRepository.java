package com.k2040.escaagnellis;

import android.content.Context;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

final class CompanionRepository {
    private static final Object MUTATION_LOCK = new Object();

    private final CompanionStateStore store;
    private final CompanionEconomy economy;

    static CompanionRepository create(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Missing context");
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) applicationContext = context;
        return new CompanionRepository(
                new SharedPreferencesCompanionStateStore(applicationContext),
                CompanionEconomy.defaultEconomy());
    }

    CompanionRepository(CompanionStateStore store, CompanionEconomy economy) {
        if (store == null || economy == null) {
            throw new IllegalArgumentException("Missing companion repository dependency");
        }
        this.store = store;
        this.economy = economy;
    }

    LoadResult load() {
        return load(System.currentTimeMillis());
    }

    LoadResult load(long nowEpochMillis) {
        LoadResult loaded = loadStored();
        if (!loaded.isUsable()) return loaded;
        CompanionState visibleState =
                loaded.state.withoutExpiredRewardGraceDeadlines(nowEpochMillis);
        return new LoadResult(
                loaded.status,
                visibleState,
                loaded.migrationRequired);
    }

    private LoadResult loadStored() {
        final Map<String, ?> values;
        try {
            values = store.readAll();
        } catch (RuntimeException ex) {
            return new LoadResult(LoadStatus.CORRUPT, CompanionState.disabledDefault());
        }
        if (values == null || values.isEmpty()) {
            return new LoadResult(LoadStatus.EMPTY, CompanionState.disabledDefault());
        }
        try {
            CompanionStateCodec.DecodeResult decoded = CompanionStateCodec.decodeResult(values);
            return new LoadResult(LoadStatus.LOADED, decoded.state, decoded.migrated);
        } catch (CompanionStateCodec.UnsupportedSchemaException ex) {
            return new LoadResult(LoadStatus.UNSUPPORTED_SCHEMA, CompanionState.disabledDefault());
        } catch (RuntimeException ex) {
            return new LoadResult(LoadStatus.CORRUPT, CompanionState.disabledDefault());
        }
    }

    MutationResult setEnabled(boolean enabled) {
        return setEnabled(enabled, System.currentTimeMillis());
    }

    MutationResult setEnabled(boolean enabled, long nowEpochMillis) {
        synchronized (MUTATION_LOCK) {
            LoadResult loaded = loadStored();
            if (!loaded.isUsable()) return MutationResult.unavailable(loaded);
            CompanionState cleaned =
                    loaded.state.withoutExpiredRewardGraceDeadlines(nowEpochMillis);
            CompanionState target = cleaned.withEnabled(enabled);
            if (!requiresWrite(loaded, target)) {
                return MutationResult.noChange(target, null, loaded.status);
            }
            return persist(loaded.state, target, null);
        }
    }

    MutationResult setReducedAnimation(boolean reducedAnimation) {
        synchronized (MUTATION_LOCK) {
            LoadResult loaded = loadStored();
            if (!loaded.isUsable()) return MutationResult.unavailable(loaded);
            CompanionState cleaned = loaded.state.withoutExpiredRewardGraceDeadlines(
                    System.currentTimeMillis());
            CompanionState target = cleaned.withReducedAnimation(reducedAnimation);
            if (!requiresWrite(loaded, target)) {
                return MutationResult.noChange(target, null, loaded.status);
            }
            return persist(loaded.state, target, null);
        }
    }

    MutationResult setCompanionName(String companionName) {
        synchronized (MUTATION_LOCK) {
            LoadResult loaded = loadStored();
            if (!loaded.isUsable()) return MutationResult.unavailable(loaded);
            CompanionState cleaned = loaded.state.withoutExpiredRewardGraceDeadlines(
                    System.currentTimeMillis());
            CompanionState target = cleaned.withCompanionName(companionName);
            if (!requiresWrite(loaded, target)) {
                return MutationResult.noChange(target, null, loaded.status);
            }
            return persist(loaded.state, target, null);
        }
    }

    MutationResult rewardDefaultPosition(
            LocalDate date,
            int position,
            long grantedAtEpochMillis) {
        synchronized (MUTATION_LOCK) {
            LoadResult loaded = loadStored();
            if (!loaded.isUsable()) return MutationResult.unavailable(loaded);
            CompanionState cleaned =
                    loaded.state.withoutExpiredRewardGraceDeadlines(grantedAtEpochMillis);
            CompanionEconomy.Result result = economy.rewardDefaultPosition(
                    cleaned,
                    date,
                    position);
            CompanionState target = result.state;
            if (result.outcome == CompanionEconomy.Outcome.REWARD_GRANTED) {
                target = target.withRewardGraceDeadline(
                        date,
                        position,
                        CompanionRewardGracePeriod.deadlineAfter(grantedAtEpochMillis));
            }
            if (!requiresWrite(loaded, target)) {
                return MutationResult.noChange(target, result.outcome, loaded.status);
            }
            return persist(loaded.state, target, result.outcome);
        }
    }

    MutationResult correctDefaultPosition(
            LocalDate date,
            int position,
            long correctedAtEpochMillis) {
        synchronized (MUTATION_LOCK) {
            LoadResult loaded = loadStored();
            if (!loaded.isUsable()) return MutationResult.unavailable(loaded);

            Long deadline = loaded.state.rewardGraceDeadline(date, position);
            CompanionState cleaned = loaded.state.withoutExpiredRewardGraceDeadlines(
                    correctedAtEpochMillis);
            if (deadline == null) {
                return persistOrNoChange(
                        loaded,
                        cleaned,
                        CompanionEconomy.Outcome.REWARD_GRACE_NOT_FOUND);
            }
            if (CompanionRewardGracePeriod.isExpired(deadline, correctedAtEpochMillis)) {
                return persistOrNoChange(
                        loaded,
                        cleaned,
                        CompanionEconomy.Outcome.REWARD_GRACE_EXPIRED);
            }

            CompanionEconomy.Result result = economy.revokeDefaultPosition(
                    cleaned,
                    date,
                    position);
            CompanionState target = result.state.withoutRewardGraceDeadline(date, position);
            return persistOrNoChange(loaded, target, result.outcome);
        }
    }

    MutationResult purchaseInteraction(String interactionId, long cost, Instant occurredAt) {
        synchronized (MUTATION_LOCK) {
            LoadResult loaded = loadStored();
            if (!loaded.isUsable()) return MutationResult.unavailable(loaded);
            if (occurredAt == null) {
                throw new IllegalArgumentException("Missing interaction timestamp");
            }
            CompanionState cleaned = loaded.state.withoutExpiredRewardGraceDeadlines(
                    occurredAt.toEpochMilli());
            long retainedBalance = Math.multiplyExact(
                    cleaned.activeRewardGraceCount(occurredAt.toEpochMilli()),
                    economy.rewardPerDefaultPosition());
            CompanionEconomy.Result result = economy.purchaseInteraction(
                    cleaned,
                    interactionId,
                    cost,
                    occurredAt,
                    retainedBalance);
            if (!requiresWrite(loaded, result.state)) {
                return MutationResult.noChange(result.state, result.outcome, loaded.status);
            }
            return persist(loaded.state, result.state, result.outcome);
        }
    }

    RestoreResult restoreBackup(CompanionState target, long nowEpochMillis) {
        if (target == null) {
            throw new IllegalArgumentException("Missing companion backup state");
        }
        if (nowEpochMillis < 0L) {
            throw new IllegalArgumentException("Epoch time must not be negative");
        }

        synchronized (MUTATION_LOCK) {
            LoadResult loaded = loadStored();
            CompanionState mergedTarget = target.companionName != null
                    ? target
                    : target.withCompanionName(loaded.state.companionName);
            CompanionState cleaned =
                    mergedTarget.withoutExpiredRewardGraceDeadlines(nowEpochMillis);
            Map<String, Object> targetValues = CompanionStateCodec.encode(cleaned);
            final Map<String, ?> currentValues;
            try {
                currentValues = store.readAll();
            } catch (RuntimeException ex) {
                return RestoreResult.storageUnavailable();
            }
            if (currentValues == null) {
                return RestoreResult.storageUnavailable();
            }
            if (targetValues.equals(currentValues)) {
                return RestoreResult.noChange(cleaned);
            }

            final CompanionStateStore.WriteOutcome writeOutcome;
            try {
                writeOutcome = store.replaceAll(targetValues);
            } catch (RuntimeException ex) {
                return RestoreResult.indeterminate();
            }

            if (writeOutcome == CompanionStateStore.WriteOutcome.TARGET_APPLIED) {
                return RestoreResult.applied(cleaned);
            }
            if (writeOutcome == CompanionStateStore.WriteOutcome.ORIGINAL_RESTORED) {
                return RestoreResult.originalRestored();
            }
            return RestoreResult.indeterminate();
        }
    }

    private MutationResult persistOrNoChange(
            LoadResult loaded,
            CompanionState target,
            CompanionEconomy.Outcome economyOutcome) {
        if (!requiresWrite(loaded, target)) {
            return MutationResult.noChange(target, economyOutcome, loaded.status);
        }
        return persist(loaded.state, target, economyOutcome);
    }

    private static boolean requiresWrite(LoadResult loaded, CompanionState target) {
        return loaded.migrationRequired || !target.equals(loaded.state);
    }

    private MutationResult persist(
            CompanionState original,
            CompanionState target,
            CompanionEconomy.Outcome economyOutcome) {
        final CompanionStateStore.WriteOutcome writeOutcome;
        try {
            writeOutcome = store.replaceAll(CompanionStateCodec.encode(target));
        } catch (RuntimeException ex) {
            return new MutationResult(
                    MutationStatus.INDETERMINATE,
                    CompanionState.disabledDefault(),
                    economyOutcome,
                    null);
        }

        if (writeOutcome == CompanionStateStore.WriteOutcome.TARGET_APPLIED) {
            return new MutationResult(
                    MutationStatus.APPLIED,
                    target,
                    economyOutcome,
                    LoadStatus.LOADED);
        }
        if (writeOutcome == CompanionStateStore.WriteOutcome.ORIGINAL_RESTORED) {
            return new MutationResult(
                    MutationStatus.ORIGINAL_RESTORED,
                    original,
                    economyOutcome,
                    LoadStatus.LOADED);
        }
        return new MutationResult(
                MutationStatus.INDETERMINATE,
                CompanionState.disabledDefault(),
                economyOutcome,
                null);
    }

    enum LoadStatus {
        EMPTY,
        LOADED,
        UNSUPPORTED_SCHEMA,
        CORRUPT
    }

    enum MutationStatus {
        APPLIED,
        NO_CHANGE,
        UNAVAILABLE,
        ORIGINAL_RESTORED,
        INDETERMINATE
    }

    enum RestoreStatus {
        APPLIED,
        NO_CHANGE,
        STORAGE_UNAVAILABLE,
        ORIGINAL_RESTORED,
        INDETERMINATE
    }

    static final class LoadResult {
        final LoadStatus status;
        final CompanionState state;
        final boolean migrationRequired;

        LoadResult(LoadStatus status, CompanionState state) {
            this(status, state, false);
        }

        LoadResult(
                LoadStatus status,
                CompanionState state,
                boolean migrationRequired) {
            this.status = status;
            this.state = state;
            this.migrationRequired = migrationRequired;
        }

        boolean isUsable() {
            return status == LoadStatus.EMPTY || status == LoadStatus.LOADED;
        }
    }

    static final class RestoreResult {
        final RestoreStatus status;
        final CompanionState state;

        private RestoreResult(RestoreStatus status, CompanionState state) {
            this.status = status;
            this.state = state;
        }

        static RestoreResult applied(CompanionState state) {
            return new RestoreResult(RestoreStatus.APPLIED, state);
        }

        static RestoreResult noChange(CompanionState state) {
            return new RestoreResult(RestoreStatus.NO_CHANGE, state);
        }

        static RestoreResult storageUnavailable() {
            return new RestoreResult(RestoreStatus.STORAGE_UNAVAILABLE, null);
        }

        static RestoreResult originalRestored() {
            return new RestoreResult(RestoreStatus.ORIGINAL_RESTORED, null);
        }

        static RestoreResult indeterminate() {
            return new RestoreResult(RestoreStatus.INDETERMINATE, null);
        }
    }

    static final class MutationResult {
        final MutationStatus status;
        final CompanionState state;
        final CompanionEconomy.Outcome economyOutcome;
        final LoadStatus loadStatus;

        MutationResult(
                MutationStatus status,
                CompanionState state,
                CompanionEconomy.Outcome economyOutcome,
                LoadStatus loadStatus) {
            this.status = status;
            this.state = state;
            this.economyOutcome = economyOutcome;
            this.loadStatus = loadStatus;
        }

        static MutationResult noChange(
                CompanionState state,
                CompanionEconomy.Outcome economyOutcome,
                LoadStatus loadStatus) {
            return new MutationResult(
                    MutationStatus.NO_CHANGE,
                    state,
                    economyOutcome,
                    loadStatus);
        }

        static MutationResult unavailable(LoadResult loaded) {
            return new MutationResult(
                    MutationStatus.UNAVAILABLE,
                    loaded.state,
                    null,
                    loaded.status);
        }
    }
}

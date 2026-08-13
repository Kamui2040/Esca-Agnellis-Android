package com.k2040.escaagnellis;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class CompanionState {
    static final int LEGACY_STORAGE_SCHEMA_VERSION = 1;
    static final int STORAGE_SCHEMA_VERSION = 2;
    static final String DEFAULT_COMPANION_ID = "default_companion";
    static final long NO_INTERACTION_TIME = 0L;

    final int storageSchemaVersion;
    final boolean enabled;
    final long balance;
    final String companionId;
    final String companionName;
    final boolean reducedAnimation;
    final String lastInteractionId;
    final long lastInteractionEpochMillis;
    final Map<LocalDate, Long> rewardLedger;
    final Map<LocalDate, Map<Integer, Long>> rewardGraceDeadlines;

    private CompanionState(
            int storageSchemaVersion,
            boolean enabled,
            long balance,
            String companionId,
            String companionName,
            boolean reducedAnimation,
            String lastInteractionId,
            long lastInteractionEpochMillis,
            Map<LocalDate, Long> rewardLedger,
            Map<LocalDate, Map<Integer, Long>> rewardGraceDeadlines) {
        if (storageSchemaVersion != STORAGE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported companion state schema: " + storageSchemaVersion);
        }
        if (balance < 0L) {
            throw new IllegalArgumentException("Companion balance must not be negative");
        }
        requireStableId(companionId, "companion id");
        companionName = normalizeCompanionName(companionName);
        if (lastInteractionId == null) {
            if (lastInteractionEpochMillis != NO_INTERACTION_TIME) {
                throw new IllegalArgumentException("Interaction timestamp requires an interaction id");
            }
        } else {
            requireStableId(lastInteractionId, "interaction id");
            if (lastInteractionEpochMillis <= NO_INTERACTION_TIME) {
                throw new IllegalArgumentException("Interaction id requires a positive timestamp");
            }
        }
        if (rewardLedger == null) {
            throw new IllegalArgumentException("Missing companion reward ledger");
        }
        if (rewardGraceDeadlines == null) {
            throw new IllegalArgumentException("Missing companion reward grace deadlines");
        }

        TreeMap<LocalDate, Long> ledgerCopy = new TreeMap<>();
        for (Map.Entry<LocalDate, Long> entry : rewardLedger.entrySet()) {
            LocalDate date = entry.getKey();
            Long mask = entry.getValue();
            if (date == null || mask == null) {
                throw new IllegalArgumentException("Invalid companion reward ledger entry");
            }
            validateRewardMask(mask);
            ledgerCopy.put(date, mask);
        }

        TreeMap<LocalDate, Map<Integer, Long>> deadlineCopy = new TreeMap<>();
        int deadlineCount = 0;
        for (Map.Entry<LocalDate, Map<Integer, Long>> dateEntry
                : rewardGraceDeadlines.entrySet()) {
            LocalDate date = dateEntry.getKey();
            Map<Integer, Long> deadlines = dateEntry.getValue();
            if (date == null || deadlines == null || deadlines.isEmpty()) {
                throw new IllegalArgumentException("Invalid companion reward grace date entry");
            }

            TreeMap<Integer, Long> deadlinesForDate = new TreeMap<>();
            for (Map.Entry<Integer, Long> deadlineEntry : deadlines.entrySet()) {
                Integer position = deadlineEntry.getKey();
                Long deadline = deadlineEntry.getValue();
                if (position == null || deadline == null) {
                    throw new IllegalArgumentException("Invalid companion reward grace entry");
                }
                validateDefaultPosition(position);
                validateRewardGraceDeadline(deadline);
                long rewardMask = ledgerCopy.containsKey(date) ? ledgerCopy.get(date) : 0L;
                if ((rewardMask & positionBit(position)) == 0L) {
                    throw new IllegalArgumentException(
                            "Reward grace deadline requires a matching reward");
                }
                deadlinesForDate.put(position, deadline);
                deadlineCount++;
            }
            deadlineCopy.put(date, Collections.unmodifiableMap(deadlinesForDate));
        }
        if (balance < deadlineCount) {
            throw new IllegalArgumentException(
                    "Companion balance cannot be below active reward grace count");
        }

        this.storageSchemaVersion = storageSchemaVersion;
        this.enabled = enabled;
        this.balance = balance;
        this.companionId = companionId;
        this.companionName = companionName;
        this.reducedAnimation = reducedAnimation;
        this.lastInteractionId = lastInteractionId;
        this.lastInteractionEpochMillis = lastInteractionEpochMillis;
        this.rewardLedger = Collections.unmodifiableMap(ledgerCopy);
        this.rewardGraceDeadlines = Collections.unmodifiableMap(deadlineCopy);
    }

    static CompanionState disabledDefault() {
        return new CompanionState(
                STORAGE_SCHEMA_VERSION,
                false,
                0L,
                DEFAULT_COMPANION_ID,
                null,
                false,
                null,
                NO_INTERACTION_TIME,
                Collections.emptyMap(),
                Collections.emptyMap());
    }

    static CompanionState restore(
            int storageSchemaVersion,
            boolean enabled,
            long balance,
            String companionId,
            boolean reducedAnimation,
            String lastInteractionId,
            long lastInteractionEpochMillis,
            Map<LocalDate, Long> rewardLedger,
            Map<LocalDate, Map<Integer, Long>> rewardGraceDeadlines) {
        return restore(
                storageSchemaVersion,
                enabled,
                balance,
                companionId,
                null,
                reducedAnimation,
                lastInteractionId,
                lastInteractionEpochMillis,
                rewardLedger,
                rewardGraceDeadlines);
    }

    static CompanionState restore(
            int storageSchemaVersion,
            boolean enabled,
            long balance,
            String companionId,
            String companionName,
            boolean reducedAnimation,
            String lastInteractionId,
            long lastInteractionEpochMillis,
            Map<LocalDate, Long> rewardLedger,
            Map<LocalDate, Map<Integer, Long>> rewardGraceDeadlines) {
        return new CompanionState(
                storageSchemaVersion,
                enabled,
                balance,
                companionId,
                companionName,
                reducedAnimation,
                lastInteractionId,
                lastInteractionEpochMillis,
                rewardLedger,
                rewardGraceDeadlines);
    }

    boolean hasReward(LocalDate date, int position) {
        requireRewardDate(date);
        validateDefaultPosition(position);
        long mask = rewardLedger.containsKey(date) ? rewardLedger.get(date) : 0L;
        return (mask & positionBit(position)) != 0L;
    }

    Long rewardGraceDeadline(LocalDate date, int position) {
        requireRewardDate(date);
        validateDefaultPosition(position);
        Map<Integer, Long> deadlines = rewardGraceDeadlines.get(date);
        return deadlines == null ? null : deadlines.get(position);
    }

    boolean hasActiveRewardGrace(LocalDate date, int position, long nowEpochMillis) {
        Long deadline = rewardGraceDeadline(date, position);
        return deadline != null
                && CompanionRewardGracePeriod.isActive(deadline, nowEpochMillis);
    }

    int activeRewardGraceCount(long nowEpochMillis) {
        int count = 0;
        for (Map<Integer, Long> deadlines : rewardGraceDeadlines.values()) {
            for (long deadline : deadlines.values()) {
                if (CompanionRewardGracePeriod.isActive(deadline, nowEpochMillis)) {
                    count++;
                }
            }
        }
        return count;
    }

    CompanionState withEnabled(boolean newEnabled) {
        if (enabled == newEnabled) return this;
        return copy(newEnabled, balance, reducedAnimation, lastInteractionId,
                lastInteractionEpochMillis, rewardLedger, rewardGraceDeadlines);
    }

    CompanionState withCompanionName(String newCompanionName) {
        String normalized = normalizeCompanionName(newCompanionName);
        if (Objects.equals(companionName, normalized)) return this;
        return copy(enabled, balance, reducedAnimation, lastInteractionId,
                lastInteractionEpochMillis, rewardLedger, rewardGraceDeadlines, normalized);
    }

    CompanionState withReducedAnimation(boolean newReducedAnimation) {
        if (reducedAnimation == newReducedAnimation) return this;
        return copy(enabled, balance, newReducedAnimation, lastInteractionId,
                lastInteractionEpochMillis, rewardLedger, rewardGraceDeadlines);
    }

    CompanionState withReward(LocalDate date, int position, long amount) {
        requireRewardDate(date);
        validateDefaultPosition(position);
        if (amount <= 0L) {
            throw new IllegalArgumentException("Reward amount must be positive");
        }
        if (hasReward(date, position)) return this;

        TreeMap<LocalDate, Long> updatedLedger = new TreeMap<>(rewardLedger);
        long currentMask = updatedLedger.containsKey(date) ? updatedLedger.get(date) : 0L;
        updatedLedger.put(date, currentMask | positionBit(position));
        long updatedBalance = Math.addExact(balance, amount);
        return copy(enabled, updatedBalance, reducedAnimation, lastInteractionId,
                lastInteractionEpochMillis, updatedLedger, rewardGraceDeadlines);
    }

    CompanionState withRewardGraceDeadline(
            LocalDate date,
            int position,
            long deadlineEpochMillis) {
        requireRewardDate(date);
        validateDefaultPosition(position);
        validateRewardGraceDeadline(deadlineEpochMillis);
        if (!hasReward(date, position)) {
            throw new IllegalArgumentException(
                    "Reward grace deadline requires a matching reward");
        }
        Long current = rewardGraceDeadline(date, position);
        if (current != null && current == deadlineEpochMillis) return this;

        TreeMap<LocalDate, Map<Integer, Long>> updatedDeadlines =
                mutableRewardGraceDeadlines();
        Map<Integer, Long> deadlinesForDate = updatedDeadlines.containsKey(date)
                ? new TreeMap<>(updatedDeadlines.get(date))
                : new TreeMap<>();
        deadlinesForDate.put(position, deadlineEpochMillis);
        updatedDeadlines.put(date, deadlinesForDate);
        return copy(enabled, balance, reducedAnimation, lastInteractionId,
                lastInteractionEpochMillis, rewardLedger, updatedDeadlines);
    }

    CompanionState withoutRewardGraceDeadline(LocalDate date, int position) {
        requireRewardDate(date);
        validateDefaultPosition(position);
        Map<Integer, Long> current = rewardGraceDeadlines.get(date);
        if (current == null || !current.containsKey(position)) return this;

        TreeMap<LocalDate, Map<Integer, Long>> updatedDeadlines =
                mutableRewardGraceDeadlines();
        TreeMap<Integer, Long> deadlinesForDate = new TreeMap<>(current);
        deadlinesForDate.remove(position);
        if (deadlinesForDate.isEmpty()) {
            updatedDeadlines.remove(date);
        } else {
            updatedDeadlines.put(date, deadlinesForDate);
        }
        return copy(enabled, balance, reducedAnimation, lastInteractionId,
                lastInteractionEpochMillis, rewardLedger, updatedDeadlines);
    }

    CompanionState withoutExpiredRewardGraceDeadlines(long nowEpochMillis) {
        if (nowEpochMillis < 0L) {
            throw new IllegalArgumentException("Epoch time must not be negative");
        }
        TreeMap<LocalDate, Map<Integer, Long>> updatedDeadlines = new TreeMap<>();
        boolean changed = false;
        for (Map.Entry<LocalDate, Map<Integer, Long>> dateEntry
                : rewardGraceDeadlines.entrySet()) {
            TreeMap<Integer, Long> activeForDate = new TreeMap<>();
            for (Map.Entry<Integer, Long> deadlineEntry : dateEntry.getValue().entrySet()) {
                if (CompanionRewardGracePeriod.isActive(
                        deadlineEntry.getValue(),
                        nowEpochMillis)) {
                    activeForDate.put(deadlineEntry.getKey(), deadlineEntry.getValue());
                } else {
                    changed = true;
                }
            }
            if (!activeForDate.isEmpty()) {
                updatedDeadlines.put(dateEntry.getKey(), activeForDate);
            }
        }
        if (!changed) return this;
        return copy(enabled, balance, reducedAnimation, lastInteractionId,
                lastInteractionEpochMillis, rewardLedger, updatedDeadlines);
    }

    CompanionState withoutReward(LocalDate date, int position, long amount) {
        requireRewardDate(date);
        validateDefaultPosition(position);
        if (amount <= 0L) {
            throw new IllegalArgumentException("Reward amount must be positive");
        }
        if (!hasReward(date, position)) return this;

        TreeMap<LocalDate, Long> updatedLedger = new TreeMap<>(rewardLedger);
        long nextMask = updatedLedger.get(date) & ~positionBit(position);
        if (nextMask == 0L) {
            updatedLedger.remove(date);
        } else {
            updatedLedger.put(date, nextMask);
        }
        if (amount > balance) {
            throw new IllegalArgumentException("Reward removal exceeds companion balance");
        }
        long updatedBalance = balance - amount;
        CompanionState withoutDeadline = withoutRewardGraceDeadline(date, position);
        return copy(
                enabled,
                updatedBalance,
                reducedAnimation,
                lastInteractionId,
                lastInteractionEpochMillis,
                updatedLedger,
                withoutDeadline.rewardGraceDeadlines);
    }

    CompanionState withPurchase(String interactionId, long cost, long occurredAtEpochMillis) {
        requireStableId(interactionId, "interaction id");
        if (cost <= 0L) {
            throw new IllegalArgumentException("Interaction cost must be positive");
        }
        if (cost > balance) {
            throw new IllegalArgumentException("Interaction cost exceeds companion balance");
        }
        if (occurredAtEpochMillis <= NO_INTERACTION_TIME) {
            throw new IllegalArgumentException("Interaction timestamp must be positive");
        }
        return copy(enabled, balance - cost, reducedAnimation, interactionId,
                occurredAtEpochMillis, rewardLedger, rewardGraceDeadlines);
    }

    private TreeMap<LocalDate, Map<Integer, Long>> mutableRewardGraceDeadlines() {
        TreeMap<LocalDate, Map<Integer, Long>> copy = new TreeMap<>();
        for (Map.Entry<LocalDate, Map<Integer, Long>> entry
                : rewardGraceDeadlines.entrySet()) {
            copy.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        }
        return copy;
    }

    private CompanionState copy(
            boolean newEnabled,
            long newBalance,
            boolean newReducedAnimation,
            String newLastInteractionId,
            long newLastInteractionEpochMillis,
            Map<LocalDate, Long> newRewardLedger,
            Map<LocalDate, Map<Integer, Long>> newRewardGraceDeadlines) {
        return copy(
                newEnabled,
                newBalance,
                newReducedAnimation,
                newLastInteractionId,
                newLastInteractionEpochMillis,
                newRewardLedger,
                newRewardGraceDeadlines,
                companionName);
    }

    private CompanionState copy(
            boolean newEnabled,
            long newBalance,
            boolean newReducedAnimation,
            String newLastInteractionId,
            long newLastInteractionEpochMillis,
            Map<LocalDate, Long> newRewardLedger,
            Map<LocalDate, Map<Integer, Long>> newRewardGraceDeadlines,
            String newCompanionName) {
        return new CompanionState(
                STORAGE_SCHEMA_VERSION,
                newEnabled,
                newBalance,
                companionId,
                newCompanionName,
                newReducedAnimation,
                newLastInteractionId,
                newLastInteractionEpochMillis,
                newRewardLedger,
                newRewardGraceDeadlines);
    }

    static String normalizeCompanionName(String value) {
        if (value == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("Invalid companion name");
            }
            if (Character.isWhitespace(c)) {
                if (builder.length() > 0) {
                    pendingSpace = true;
                }
                continue;
            }
            if (pendingSpace) {
                builder.append(' ');
                pendingSpace = false;
            }
            builder.append(c);
            if (builder.length() > 24) {
                throw new IllegalArgumentException("Companion name is too long");
            }
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    static String requireStableId(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + label);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9'
                    || c == '_';
            if (!valid) {
                throw new IllegalArgumentException("Invalid " + label + ": " + value);
            }
        }
        return value;
    }

    static void validateRewardMask(long mask) {
        if (mask <= 0L || (mask & ~validRewardMask()) != 0L) {
            throw new IllegalArgumentException("Invalid companion reward mask");
        }
    }

    static void validateDefaultPosition(int position) {
        if (position < 0 || position >= PyramidScheme.TILE_COUNT) {
            throw new IllegalArgumentException("Invalid default pyramid position: " + position);
        }
    }

    static void validateRewardGraceDeadline(long deadlineEpochMillis) {
        if (deadlineEpochMillis < CompanionRewardGracePeriod.DURATION_MILLIS) {
            throw new IllegalArgumentException("Reward grace deadline is too early");
        }
    }

    private static void requireRewardDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Missing reward date");
        }
    }

    private static long positionBit(int position) {
        return 1L << position;
    }

    private static long validRewardMask() {
        if (PyramidScheme.TILE_COUNT <= 0 || PyramidScheme.TILE_COUNT >= Long.SIZE) {
            throw new IllegalStateException("Unsupported pyramid tile count");
        }
        return (1L << PyramidScheme.TILE_COUNT) - 1L;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CompanionState)) return false;
        CompanionState that = (CompanionState) other;
        return storageSchemaVersion == that.storageSchemaVersion
                && enabled == that.enabled
                && balance == that.balance
                && reducedAnimation == that.reducedAnimation
                && lastInteractionEpochMillis == that.lastInteractionEpochMillis
                && companionId.equals(that.companionId)
                && Objects.equals(companionName, that.companionName)
                && Objects.equals(lastInteractionId, that.lastInteractionId)
                && rewardLedger.equals(that.rewardLedger)
                && rewardGraceDeadlines.equals(that.rewardGraceDeadlines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                storageSchemaVersion,
                enabled,
                balance,
                companionId,
                companionName,
                reducedAnimation,
                lastInteractionId,
                lastInteractionEpochMillis,
                rewardLedger,
                rewardGraceDeadlines);
    }
}

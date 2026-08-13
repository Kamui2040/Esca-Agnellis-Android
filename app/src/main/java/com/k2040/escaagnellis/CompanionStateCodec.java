package com.k2040.escaagnellis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class CompanionStateCodec {
    static final String KEY_STORAGE_SCHEMA_VERSION = "storage_schema_version";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_BALANCE = "balance";
    static final String KEY_COMPANION_ID = "companion_id";
    static final String KEY_COMPANION_NAME = "companion_name";
    static final String KEY_REDUCED_ANIMATION = "reduced_animation";
    static final String KEY_LAST_INTERACTION_ID = "last_interaction_id";
    static final String KEY_LAST_INTERACTION_EPOCH_MILLIS = "last_interaction_epoch_millis";
    static final String KEY_REWARD_LEDGER = "reward_ledger";
    static final String KEY_REWARD_GRACE_DEADLINES = "reward_grace_deadlines";
    private static final char FIELD_SEPARATOR = '#';

    private CompanionStateCodec() {
    }

    static Map<String, Object> encode(CompanionState state) {
        if (state == null) {
            throw new IllegalArgumentException("Missing companion state");
        }
        LinkedHashSet<String> ledger = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, Long> entry : state.rewardLedger.entrySet()) {
            ledger.add(entry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    + FIELD_SEPARATOR
                    + Long.toHexString(entry.getValue()));
        }

        LinkedHashSet<String> graceDeadlines = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, Map<Integer, Long>> dateEntry
                : state.rewardGraceDeadlines.entrySet()) {
            String dateText = dateEntry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE);
            for (Map.Entry<Integer, Long> deadlineEntry : dateEntry.getValue().entrySet()) {
                graceDeadlines.add(dateText
                        + FIELD_SEPARATOR
                        + deadlineEntry.getKey()
                        + FIELD_SEPARATOR
                        + deadlineEntry.getValue());
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_STORAGE_SCHEMA_VERSION, CompanionState.STORAGE_SCHEMA_VERSION);
        values.put(KEY_ENABLED, state.enabled);
        values.put(KEY_BALANCE, state.balance);
        values.put(KEY_COMPANION_ID, state.companionId);
        if (state.companionName != null) {
            values.put(KEY_COMPANION_NAME, state.companionName);
        }
        values.put(KEY_REDUCED_ANIMATION, state.reducedAnimation);
        values.put(KEY_LAST_INTERACTION_ID,
                state.lastInteractionId == null ? "" : state.lastInteractionId);
        values.put(KEY_LAST_INTERACTION_EPOCH_MILLIS, state.lastInteractionEpochMillis);
        values.put(KEY_REWARD_LEDGER, ledger);
        values.put(KEY_REWARD_GRACE_DEADLINES, graceDeadlines);
        return values;
    }

    static CompanionState decode(Map<String, ?> values) {
        return decodeResult(values).state;
    }

    static DecodeResult decodeResult(Map<String, ?> values) {
        if (values == null) {
            throw new IllegalArgumentException("Missing companion state values");
        }

        int storedSchemaVersion = requireInteger(values, KEY_STORAGE_SCHEMA_VERSION);
        if (storedSchemaVersion != CompanionState.LEGACY_STORAGE_SCHEMA_VERSION
                && storedSchemaVersion != CompanionState.STORAGE_SCHEMA_VERSION) {
            throw new UnsupportedSchemaException(storedSchemaVersion);
        }

        boolean enabled = requireBoolean(values, KEY_ENABLED);
        long balance = requireLong(values, KEY_BALANCE);
        String companionId = requireString(values, KEY_COMPANION_ID);
        String companionName = values.containsKey(KEY_COMPANION_NAME)
                ? requireString(values, KEY_COMPANION_NAME)
                : null;
        boolean reducedAnimation = requireBoolean(values, KEY_REDUCED_ANIMATION);
        String storedInteractionId = requireString(values, KEY_LAST_INTERACTION_ID);
        String interactionId = storedInteractionId.isEmpty() ? null : storedInteractionId;
        long interactionTime = requireLong(values, KEY_LAST_INTERACTION_EPOCH_MILLIS);
        Map<LocalDate, Long> ledger = decodeRewardLedger(
                requireStringSet(values, KEY_REWARD_LEDGER));

        Map<LocalDate, Map<Integer, Long>> graceDeadlines;
        boolean migrated = storedSchemaVersion == CompanionState.LEGACY_STORAGE_SCHEMA_VERSION;
        if (migrated) {
            graceDeadlines = Collections.emptyMap();
        } else {
            graceDeadlines = decodeRewardGraceDeadlines(
                    requireStringSet(values, KEY_REWARD_GRACE_DEADLINES));
        }

        CompanionState state = CompanionState.restore(
                CompanionState.STORAGE_SCHEMA_VERSION,
                enabled,
                balance,
                companionId,
                companionName,
                reducedAnimation,
                interactionId,
                interactionTime,
                ledger,
                graceDeadlines);
        return new DecodeResult(state, migrated);
    }

    private static Map<LocalDate, Long> decodeRewardLedger(Set<String> storedLedger) {
        TreeMap<LocalDate, Long> ledger = new TreeMap<>();
        for (String entry : storedLedger) {
            ParsedLedgerEntry parsed = parseLedgerEntry(entry);
            if (ledger.put(parsed.date, parsed.mask) != null) {
                throw new IllegalArgumentException("Duplicate companion reward date");
            }
        }
        return ledger;
    }

    private static Map<LocalDate, Map<Integer, Long>> decodeRewardGraceDeadlines(
            Set<String> storedDeadlines) {
        TreeMap<LocalDate, Map<Integer, Long>> deadlines = new TreeMap<>();
        for (String entry : storedDeadlines) {
            ParsedGraceDeadline parsed = parseGraceDeadline(entry);
            Map<Integer, Long> deadlinesForDate = deadlines.containsKey(parsed.date)
                    ? deadlines.get(parsed.date)
                    : new TreeMap<>();
            if (deadlinesForDate.put(parsed.position, parsed.deadlineEpochMillis) != null) {
                throw new IllegalArgumentException("Duplicate companion reward grace deadline");
            }
            deadlines.put(parsed.date, deadlinesForDate);
        }
        return deadlines;
    }

    private static ParsedLedgerEntry parseLedgerEntry(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing reward ledger entry");
        }
        int separator = value.lastIndexOf(FIELD_SEPARATOR);
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Malformed reward ledger entry");
        }
        try {
            LocalDate date = parseCanonicalDate(value.substring(0, separator));
            String maskText = value.substring(separator + 1);
            long mask = Long.parseLong(maskText, 16);
            CompanionState.validateRewardMask(mask);
            if (!maskText.equals(Long.toHexString(mask))) {
                throw new IllegalArgumentException("Non-canonical reward mask");
            }
            return new ParsedLedgerEntry(date, mask);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Malformed reward ledger entry", ex);
        }
    }

    private static ParsedGraceDeadline parseGraceDeadline(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing reward grace deadline entry");
        }
        int firstSeparator = value.indexOf(FIELD_SEPARATOR);
        int lastSeparator = value.lastIndexOf(FIELD_SEPARATOR);
        if (firstSeparator <= 0
                || lastSeparator <= firstSeparator + 1
                || lastSeparator == value.length() - 1) {
            throw new IllegalArgumentException("Malformed reward grace deadline entry");
        }
        try {
            LocalDate date = parseCanonicalDate(value.substring(0, firstSeparator));
            String positionText = value.substring(firstSeparator + 1, lastSeparator);
            int position = Integer.parseInt(positionText);
            CompanionState.validateDefaultPosition(position);
            if (!positionText.equals(Integer.toString(position))) {
                throw new IllegalArgumentException("Non-canonical reward position");
            }
            String deadlineText = value.substring(lastSeparator + 1);
            long deadline = Long.parseLong(deadlineText);
            CompanionState.validateRewardGraceDeadline(deadline);
            if (!deadlineText.equals(Long.toString(deadline))) {
                throw new IllegalArgumentException("Non-canonical reward grace deadline");
            }
            return new ParsedGraceDeadline(date, position, deadline);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Malformed reward grace deadline entry", ex);
        }
    }

    private static LocalDate parseCanonicalDate(String dateText) {
        LocalDate date = LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE);
        if (!dateText.equals(date.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
            throw new IllegalArgumentException("Non-canonical reward date");
        }
        return date;
    }

    private static int requireInteger(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException("Invalid integer companion state field: " + key);
        }
        return (Integer) value;
    }

    private static long requireLong(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (!(value instanceof Long)) {
            throw new IllegalArgumentException("Invalid long companion state field: " + key);
        }
        return (Long) value;
    }

    private static boolean requireBoolean(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid boolean companion state field: " + key);
        }
        return (Boolean) value;
    }

    private static String requireString(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Invalid string companion state field: " + key);
        }
        return (String) value;
    }

    private static Set<String> requireStringSet(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (!(value instanceof Set<?>)) {
            throw new IllegalArgumentException("Invalid string-set companion state field: " + key);
        }
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (Object entry : (Set<?>) value) {
            if (!(entry instanceof String)) {
                throw new IllegalArgumentException("Invalid companion string-set value: " + key);
            }
            copy.add((String) entry);
        }
        return copy;
    }

    private static Object requireValue(Map<String, ?> values, String key) {
        if (!values.containsKey(key) || values.get(key) == null) {
            throw new IllegalArgumentException("Missing companion state field: " + key);
        }
        return values.get(key);
    }

    static final class DecodeResult {
        final CompanionState state;
        final boolean migrated;

        DecodeResult(CompanionState state, boolean migrated) {
            this.state = state;
            this.migrated = migrated;
        }
    }

    private static final class ParsedLedgerEntry {
        final LocalDate date;
        final long mask;

        ParsedLedgerEntry(LocalDate date, long mask) {
            this.date = date;
            this.mask = mask;
        }
    }

    private static final class ParsedGraceDeadline {
        final LocalDate date;
        final int position;
        final long deadlineEpochMillis;

        ParsedGraceDeadline(LocalDate date, int position, long deadlineEpochMillis) {
            this.date = date;
            this.position = position;
            this.deadlineEpochMillis = deadlineEpochMillis;
        }
    }

    static final class UnsupportedSchemaException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        final int storageSchemaVersion;

        UnsupportedSchemaException(int storageSchemaVersion) {
            super("Unsupported companion state schema: " + storageSchemaVersion);
            this.storageSchemaVersion = storageSchemaVersion;
        }
    }
}

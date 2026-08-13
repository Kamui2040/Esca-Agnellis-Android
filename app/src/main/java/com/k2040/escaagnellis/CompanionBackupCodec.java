package com.k2040.escaagnellis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CompanionBackupCodec {
    static final String SCHEMA_NAME = "esca-agnellis-companion-backup";
    static final int SCHEMA_VERSION = 1;

    static final String KEY_SCHEMA_NAME = "schemaName";
    static final String KEY_SCHEMA_VERSION = "schemaVersion";
    static final String KEY_COMPANION_STORAGE_SCHEMA_VERSION =
            "companionStorageSchemaVersion";
    static final String KEY_STATE = "state";

    static final String KEY_ENABLED = "enabled";
    static final String KEY_BALANCE = "balance";
    static final String KEY_COMPANION_ID = "companionId";
    static final String KEY_REDUCED_ANIMATION = "reducedAnimation";
    static final String KEY_LAST_INTERACTION_ID = "lastInteractionId";
    static final String KEY_LAST_INTERACTION_EPOCH_MILLIS =
            "lastInteractionEpochMillis";
    static final String KEY_REWARD_LEDGER = "rewardLedger";
    static final String KEY_REWARD_GRACE_DEADLINES = "rewardGraceDeadlines";

    static final String KEY_DATE = "date";
    static final String KEY_REWARD_MASK = "rewardMask";
    static final String KEY_POSITION = "position";
    static final String KEY_DEADLINE_EPOCH_MILLIS = "deadlineEpochMillis";

    private static final int MAX_REWARD_DATES = 10_000;
    private static final int MAX_REWARD_DEADLINES =
            MAX_REWARD_DATES * PyramidScheme.TILE_COUNT;

    private static final Set<String> ROOT_KEYS = immutableKeySet(
            KEY_SCHEMA_NAME,
            KEY_SCHEMA_VERSION,
            KEY_COMPANION_STORAGE_SCHEMA_VERSION,
            KEY_STATE);
    private static final Set<String> STATE_KEYS = immutableKeySet(
            KEY_ENABLED,
            KEY_BALANCE,
            KEY_COMPANION_ID,
            KEY_REDUCED_ANIMATION,
            KEY_LAST_INTERACTION_ID,
            KEY_LAST_INTERACTION_EPOCH_MILLIS,
            KEY_REWARD_LEDGER,
            KEY_REWARD_GRACE_DEADLINES);
    private static final Set<String> LEDGER_ENTRY_KEYS = immutableKeySet(
            KEY_DATE,
            KEY_REWARD_MASK);
    private static final Set<String> DEADLINE_ENTRY_KEYS = immutableKeySet(
            KEY_DATE,
            KEY_POSITION,
            KEY_DEADLINE_EPOCH_MILLIS);

    private CompanionBackupCodec() {
    }

    static Map<String, Object> encode(CompanionState state) {
        if (state == null) {
            throw new IllegalArgumentException("Missing companion backup state");
        }

        List<Map<String, Object>> ledger = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> entry : state.rewardLedger.entrySet()) {
            Map<String, Object> encodedEntry = new LinkedHashMap<>();
            encodedEntry.put(
                    KEY_DATE,
                    entry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE));
            encodedEntry.put(KEY_REWARD_MASK, Long.toHexString(entry.getValue()));
            ledger.add(Collections.unmodifiableMap(encodedEntry));
        }

        List<Map<String, Object>> deadlines = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<Integer, Long>> dateEntry
                : state.rewardGraceDeadlines.entrySet()) {
            String date = dateEntry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE);
            for (Map.Entry<Integer, Long> deadlineEntry
                    : dateEntry.getValue().entrySet()) {
                Map<String, Object> encodedEntry = new LinkedHashMap<>();
                encodedEntry.put(KEY_DATE, date);
                encodedEntry.put(KEY_POSITION, deadlineEntry.getKey());
                encodedEntry.put(
                        KEY_DEADLINE_EPOCH_MILLIS,
                        Long.toString(deadlineEntry.getValue()));
                deadlines.add(Collections.unmodifiableMap(encodedEntry));
            }
        }

        Map<String, Object> encodedState = new LinkedHashMap<>();
        encodedState.put(KEY_ENABLED, state.enabled);
        encodedState.put(KEY_BALANCE, Long.toString(state.balance));
        encodedState.put(KEY_COMPANION_ID, state.companionId);
        encodedState.put(KEY_REDUCED_ANIMATION, state.reducedAnimation);
        encodedState.put(
                KEY_LAST_INTERACTION_ID,
                state.lastInteractionId == null ? "" : state.lastInteractionId);
        encodedState.put(
                KEY_LAST_INTERACTION_EPOCH_MILLIS,
                Long.toString(state.lastInteractionEpochMillis));
        encodedState.put(
                KEY_REWARD_LEDGER,
                Collections.unmodifiableList(ledger));
        encodedState.put(
                KEY_REWARD_GRACE_DEADLINES,
                Collections.unmodifiableList(deadlines));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put(KEY_SCHEMA_NAME, SCHEMA_NAME);
        document.put(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        document.put(
                KEY_COMPANION_STORAGE_SCHEMA_VERSION,
                CompanionState.STORAGE_SCHEMA_VERSION);
        document.put(KEY_STATE, Collections.unmodifiableMap(encodedState));
        return Collections.unmodifiableMap(document);
    }

    static CompanionState decode(Map<String, ?> document) {
        if (document == null) {
            throw new IllegalArgumentException("Missing companion backup document");
        }

        String schemaName = requireString(document, KEY_SCHEMA_NAME, false);
        if (!SCHEMA_NAME.equals(schemaName)) {
            throw new WrongFormatException(schemaName);
        }

        requireExactKeys(document, ROOT_KEYS, "companion backup document");

        int schemaVersion = requireInteger(document, KEY_SCHEMA_VERSION);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new UnsupportedSchemaException(schemaVersion);
        }

        int companionStorageSchemaVersion =
                requireInteger(document, KEY_COMPANION_STORAGE_SCHEMA_VERSION);
        if (companionStorageSchemaVersion != CompanionState.STORAGE_SCHEMA_VERSION) {
            throw new UnsupportedStateSchemaException(companionStorageSchemaVersion);
        }

        Map<String, ?> encodedState = requireMap(document, KEY_STATE);
        requireExactKeys(encodedState, STATE_KEYS, "companion backup state");

        boolean enabled = requireBoolean(encodedState, KEY_ENABLED);
        long balance = requireCanonicalLong(encodedState, KEY_BALANCE);
        String companionId = requireString(encodedState, KEY_COMPANION_ID, false);
        boolean reducedAnimation =
                requireBoolean(encodedState, KEY_REDUCED_ANIMATION);
        String lastInteractionId =
                requireString(encodedState, KEY_LAST_INTERACTION_ID, true);
        long lastInteractionEpochMillis = requireCanonicalLong(
                encodedState,
                KEY_LAST_INTERACTION_EPOCH_MILLIS);

        LinkedHashSet<String> rewardLedger = decodeRewardLedger(
                requireList(encodedState, KEY_REWARD_LEDGER));
        LinkedHashSet<String> rewardGraceDeadlines = decodeRewardGraceDeadlines(
                requireList(encodedState, KEY_REWARD_GRACE_DEADLINES));

        Map<String, Object> stateValues = new LinkedHashMap<>();
        stateValues.put(
                CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION,
                CompanionState.STORAGE_SCHEMA_VERSION);
        stateValues.put(CompanionStateCodec.KEY_ENABLED, enabled);
        stateValues.put(CompanionStateCodec.KEY_BALANCE, balance);
        stateValues.put(CompanionStateCodec.KEY_COMPANION_ID, companionId);
        stateValues.put(
                CompanionStateCodec.KEY_REDUCED_ANIMATION,
                reducedAnimation);
        stateValues.put(
                CompanionStateCodec.KEY_LAST_INTERACTION_ID,
                lastInteractionId);
        stateValues.put(
                CompanionStateCodec.KEY_LAST_INTERACTION_EPOCH_MILLIS,
                lastInteractionEpochMillis);
        stateValues.put(CompanionStateCodec.KEY_REWARD_LEDGER, rewardLedger);
        stateValues.put(
                CompanionStateCodec.KEY_REWARD_GRACE_DEADLINES,
                rewardGraceDeadlines);
        return CompanionStateCodec.decode(stateValues);
    }

    private static LinkedHashSet<String> decodeRewardLedger(List<?> entries) {
        if (entries.size() > MAX_REWARD_DATES) {
            throw new IllegalArgumentException("Too many companion reward dates");
        }

        LinkedHashSet<String> encodedEntries = new LinkedHashSet<>();
        Set<LocalDate> seenDates = new HashSet<>();
        LocalDate previousDate = null;
        for (Object value : entries) {
            Map<String, ?> entry = requireMapValue(value, "reward ledger entry");
            requireExactKeys(entry, LEDGER_ENTRY_KEYS, "reward ledger entry");

            LocalDate date = requireCanonicalDate(entry, KEY_DATE);
            if (!seenDates.add(date)) {
                throw new IllegalArgumentException("Duplicate companion reward date");
            }
            if (previousDate != null && date.compareTo(previousDate) <= 0) {
                throw new IllegalArgumentException(
                        "Companion reward ledger is not canonically ordered");
            }
            previousDate = date;

            String maskText = requireString(entry, KEY_REWARD_MASK, false);
            long mask = parseCanonicalHex(maskText);
            CompanionState.validateRewardMask(mask);
            encodedEntries.add(
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            + "#"
                            + maskText);
        }
        return encodedEntries;
    }

    private static LinkedHashSet<String> decodeRewardGraceDeadlines(List<?> entries) {
        if (entries.size() > MAX_REWARD_DEADLINES) {
            throw new IllegalArgumentException(
                    "Too many companion reward grace deadlines");
        }

        LinkedHashSet<String> encodedEntries = new LinkedHashSet<>();
        Set<String> seenKeys = new HashSet<>();
        LocalDate previousDate = null;
        int previousPosition = -1;
        for (Object value : entries) {
            Map<String, ?> entry = requireMapValue(value, "reward deadline entry");
            requireExactKeys(
                    entry,
                    DEADLINE_ENTRY_KEYS,
                    "reward deadline entry");

            LocalDate date = requireCanonicalDate(entry, KEY_DATE);
            int position = requireInteger(entry, KEY_POSITION);
            CompanionState.validateDefaultPosition(position);
            long deadline = requireCanonicalLong(
                    entry,
                    KEY_DEADLINE_EPOCH_MILLIS);
            CompanionState.validateRewardGraceDeadline(deadline);

            String uniqueKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    + "#"
                    + position;
            if (!seenKeys.add(uniqueKey)) {
                throw new IllegalArgumentException(
                        "Duplicate companion reward grace deadline");
            }
            if (previousDate != null) {
                int dateComparison = date.compareTo(previousDate);
                if (dateComparison < 0
                        || (dateComparison == 0 && position <= previousPosition)) {
                    throw new IllegalArgumentException(
                            "Companion reward deadlines are not canonically ordered");
                }
            }
            previousDate = date;
            previousPosition = position;

            encodedEntries.add(
                    uniqueKey
                            + "#"
                            + Long.toString(deadline));
        }
        return encodedEntries;
    }

    private static LocalDate requireCanonicalDate(
            Map<String, ?> values,
            String key) {
        String text = requireString(values, key, false);
        try {
            LocalDate date = LocalDate.parse(
                    text,
                    DateTimeFormatter.ISO_LOCAL_DATE);
            if (!text.equals(date.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
                throw new IllegalArgumentException("Non-canonical backup date");
            }
            return date;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid companion backup date", ex);
        }
    }

    private static long parseCanonicalHex(String text) {
        try {
            long value = Long.parseLong(text, 16);
            if (!text.equals(Long.toHexString(value))) {
                throw new IllegalArgumentException(
                        "Non-canonical companion reward mask");
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Invalid companion reward mask",
                    ex);
        }
    }

    private static long requireCanonicalLong(
            Map<String, ?> values,
            String key) {
        String text = requireString(values, key, false);
        try {
            long value = Long.parseLong(text);
            if (!text.equals(Long.toString(value))) {
                throw new IllegalArgumentException(
                        "Non-canonical companion long field: " + key);
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Invalid companion long field: " + key,
                    ex);
        }
    }

    private static int requireInteger(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            long longValue = (Long) value;
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
        }
        throw new IllegalArgumentException(
                "Invalid companion integer field: " + key);
    }

    private static boolean requireBoolean(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(
                    "Invalid companion boolean field: " + key);
        }
        return (Boolean) value;
    }

    private static String requireString(
            Map<String, ?> values,
            String key,
            boolean allowEmpty) {
        Object value = requireValue(values, key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                    "Invalid companion string field: " + key);
        }
        String text = (String) value;
        if (!allowEmpty && text.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty companion string field: " + key);
        }
        return text;
    }

    private static List<?> requireList(Map<String, ?> values, String key) {
        Object value = requireValue(values, key);
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException(
                    "Invalid companion list field: " + key);
        }
        return (List<?>) value;
    }

    private static Map<String, ?> requireMap(
            Map<String, ?> values,
            String key) {
        return requireMapValue(requireValue(values, key), key);
    }

    private static Map<String, ?> requireMapValue(Object value, String label) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "Invalid companion map value: " + label);
        }
        Map<?, ?> raw = (Map<?, ?>) value;
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String) || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Invalid companion map entry: " + label);
            }
            copy.put((String) entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private static Object requireValue(Map<String, ?> values, String key) {
        if (!values.containsKey(key) || values.get(key) == null) {
            throw new IllegalArgumentException(
                    "Missing companion backup field: " + key);
        }
        return values.get(key);
    }

    private static void requireExactKeys(
            Map<String, ?> values,
            Set<String> expected,
            String label) {
        Set<String> actual = new HashSet<>();
        for (Object key : values.keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException(
                        "Invalid key in " + label);
            }
            actual.add((String) key);
        }
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "Unexpected fields in " + label);
        }
    }

    private static Set<String> immutableKeySet(String... keys) {
        return Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(keys)));
    }

    static final class WrongFormatException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        final String schemaName;

        WrongFormatException(String schemaName) {
            super("Unsupported companion backup schema name: " + schemaName);
            this.schemaName = schemaName;
        }
    }

    static final class UnsupportedSchemaException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        final int schemaVersion;

        UnsupportedSchemaException(int schemaVersion) {
            super("Unsupported companion backup schema: " + schemaVersion);
            this.schemaVersion = schemaVersion;
        }
    }

    static final class UnsupportedStateSchemaException
            extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        final int storageSchemaVersion;

        UnsupportedStateSchemaException(int storageSchemaVersion) {
            super("Unsupported companion state schema: " + storageSchemaVersion);
            this.storageSchemaVersion = storageSchemaVersion;
        }
    }
}

package com.k2040.escaagnellis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class PyramidScheme {
    static final String PREFERENCES_NAME = "esca_public_v1";
    static final String SELECTED_DATE_KEY = "selected_date";
    static final int BACKUP_SCHEMA_VERSION = 2;

    static final int TILE_COUNT = 22;
    static final int ROW_COUNT = 6;

    static final int SUBTYPE_EXTRAS = 0;
    static final int SUBTYPE_OILS_FATS = 1;
    static final int SUBTYPE_NUTS_SEEDS = 2;
    static final int SUBTYPE_MILK_DAIRY = 3;
    static final int SUBTYPE_PROTEIN = 4;
    static final int SUBTYPE_GRAINS = 5;
    static final int SUBTYPE_SIDES = 6;
    static final int SUBTYPE_PRODUCE = 7;
    static final int SUBTYPE_DRINKS = 8;
    static final int SUBTYPE_COUNT = 9;

    static final String GROUP_EXTRAS = "extras";
    static final String GROUP_OILS_FATS = "oils_fats";
    static final String GROUP_NUTS_SEEDS = "nuts_seeds";
    static final String GROUP_MILK_DAIRY = "milk_dairy";
    static final String GROUP_LEGUMES_MEAT_FISH_EGG = "legumes_meat_fish_egg";
    static final String GROUP_BREAD_GRAINS_SIDES = "bread_grains_sides";
    static final String GROUP_FRUIT_VEGETABLES = "fruit_vegetables";
    static final String GROUP_DRINKS = "drinks";

    static final String[] GROUP_IDS = new String[] {
            GROUP_EXTRAS,
            GROUP_OILS_FATS,
            GROUP_NUTS_SEEDS,
            GROUP_MILK_DAIRY,
            GROUP_LEGUMES_MEAT_FISH_EGG,
            GROUP_BREAD_GRAINS_SIDES,
            GROUP_FRUIT_VEGETABLES,
            GROUP_DRINKS
    };

    static final int[] GROUP_TARGETS = new int[] { 1, 2, 1, 2, 1, 4, 5, 6 };

    static final int[] SUBTYPE_BY_POSITION = new int[] {
            SUBTYPE_EXTRAS,
            SUBTYPE_OILS_FATS, SUBTYPE_OILS_FATS, SUBTYPE_NUTS_SEEDS,
            SUBTYPE_MILK_DAIRY, SUBTYPE_MILK_DAIRY, SUBTYPE_PROTEIN,
            SUBTYPE_GRAINS, SUBTYPE_GRAINS, SUBTYPE_GRAINS, SUBTYPE_SIDES,
            SUBTYPE_PRODUCE, SUBTYPE_PRODUCE, SUBTYPE_PRODUCE,
            SUBTYPE_PRODUCE, SUBTYPE_PRODUCE,
            SUBTYPE_DRINKS, SUBTYPE_DRINKS, SUBTYPE_DRINKS,
            SUBTYPE_DRINKS, SUBTYPE_DRINKS, SUBTYPE_DRINKS
    };

    static final int[] SUBTYPE_STARTS = new int[] { 0, 1, 3, 4, 6, 7, 10, 11, 16 };
    static final int[] SUBTYPE_DEFAULT_COUNTS = new int[] { 1, 2, 1, 2, 1, 3, 1, 5, 6 };
    static final int MAX_EXTRA_COUNT = 1_000_000;

    enum TransactionOutcome {
        TARGET_APPLIED,
        ORIGINAL_RESTORED,
        INDETERMINATE
    }

    private PyramidScheme() {
    }

    static void requireCurrentBackupSchema(int schemaVersion) {
        if (schemaVersion != BACKUP_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported backup schema version: " + schemaVersion);
        }
    }

    static Set<String> currentGenericBackupPreferenceKeys() {
        return Collections.emptySet();
    }

    static void requireCurrentGenericBackupPreferenceKeys(Set<String> keys) {
        if (keys == null) {
            throw new IllegalArgumentException("Missing generic backup preference keys");
        }
        Set<String> supported = currentGenericBackupPreferenceKeys();
        for (String key : keys) {
            if (key == null || !supported.contains(key)) {
                throw new IllegalArgumentException(
                        "Unsupported generic backup preference key: " + key);
            }
        }
    }

    static DayState emptyDay() {
        return new DayState(new boolean[TILE_COUNT], new int[SUBTYPE_COUNT]);
    }

    static DayState fromBackupArrays(boolean[] ticks, int[] subtypeExtras) {
        requireLength(ticks, TILE_COUNT, "ticks");
        requireLength(subtypeExtras, SUBTYPE_COUNT, "subtype extras");
        int[] safeExtras = subtypeExtras.clone();
        validateNonNegative(safeExtras, "subtype extras");
        return new DayState(ticks.clone(), safeExtras);
    }

    static DayState parseStoredDay(String serialized) {
        if (serialized == null) return emptyDay();
        String[] parts = serialized.split("\\|", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid stored day field count");
        }
        return new DayState(
                parseTicks(parts[0]),
                parseIntegers(parts[1], SUBTYPE_COUNT));
    }

    static String serialize(DayState day) {
        DayState validated = fromBackupArrays(day.ticks, day.subtypeExtras);
        StringBuilder value = new StringBuilder(TILE_COUNT + 32);
        for (boolean tick : validated.ticks) value.append(tick ? '1' : '0');
        value.append('|');
        appendIntegers(value, validated.subtypeExtras);
        return value.toString();
    }

    static BackupModel prepareBackupModel(
            int schemaVersion,
            Map<String, String> serializedDays,
            String selectedDate) {
        requireCurrentBackupSchema(schemaVersion);
        if (serializedDays == null || selectedDate == null) {
            throw new IllegalArgumentException("Malformed backup document");
        }
        String canonicalSelectedDate = requireCanonicalDate(selectedDate);
        Map<String, String> prepared = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : serializedDays.entrySet()) {
            String date = requireCanonicalDate(entry.getKey());
            if (prepared.containsKey(date)) {
                throw new IllegalArgumentException("Duplicate backup date");
            }
            DayState parsed = parseStoredDay(entry.getValue());
            prepared.put(date, serialize(parsed));
        }
        return new BackupModel(prepared, canonicalSelectedDate);
    }

    static Map<String, Object> copyPreferenceSnapshot(Map<String, ?> source) {
        if (source == null) {
            throw new IllegalArgumentException("Missing preference snapshot");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                throw new IllegalArgumentException("Invalid preference snapshot entry");
            }
            if (value instanceof String
                    || value instanceof Boolean
                    || value instanceof Integer
                    || value instanceof Long
                    || value instanceof Float) {
                copy.put(key, value);
            } else if (value instanceof Set<?>) {
                Set<String> setCopy = new HashSet<>();
                for (Object item : (Set<?>) value) {
                    if (!(item instanceof String)) {
                        throw new IllegalArgumentException("Invalid preference string set");
                    }
                    setCopy.add((String) item);
                }
                copy.put(key, setCopy);
            } else {
                throw new IllegalArgumentException("Unsupported preference snapshot value");
            }
        }
        return copy;
    }

    static boolean preferenceSnapshotsEqual(Map<String, ?> first, Map<String, ?> second) {
        return copyPreferenceSnapshot(first).equals(copyPreferenceSnapshot(second));
    }

    static PreferenceTransactionResult classifyPreferenceTransaction(
            boolean targetCommitReported,
            Map<String, ?> targetSnapshot,
            Map<String, ?> observedAfterTarget,
            boolean rollbackAttempted,
            boolean originalCommitReported,
            Map<String, ?> originalSnapshot,
            Map<String, ?> observedAfterRollback) {
        TransactionOutcome outcome;
        if (preferenceSnapshotsEqual(targetSnapshot, observedAfterTarget)) {
            outcome = TransactionOutcome.TARGET_APPLIED;
        } else if (rollbackAttempted
                && preferenceSnapshotsEqual(originalSnapshot, observedAfterRollback)) {
            outcome = TransactionOutcome.ORIGINAL_RESTORED;
        } else {
            outcome = TransactionOutcome.INDETERMINATE;
        }
        return new PreferenceTransactionResult(
                outcome,
                targetCommitReported,
                rollbackAttempted,
                originalCommitReported);
    }

    static boolean fillSubtype(boolean[] ticks, int subtype) {
        requireSubtype(subtype);
        return PyramidInteractionRules.fillFirstUnticked(
                ticks,
                SUBTYPE_STARTS[subtype],
                SUBTYPE_DEFAULT_COUNTS[subtype]);
    }

    static boolean removeSubtype(boolean[] ticks, int[] subtypeExtras, int subtype) {
        requireSubtype(subtype);
        requireLength(subtypeExtras, SUBTYPE_COUNT, "subtype extras");
        return PyramidInteractionRules.removeFromRight(
                ticks,
                SUBTYPE_STARTS[subtype],
                SUBTYPE_DEFAULT_COUNTS[subtype],
                subtypeExtras,
                subtype);
    }

    static boolean isSubtypeComplete(boolean[] ticks, int subtype) {
        requireSubtype(subtype);
        int start = SUBTYPE_STARTS[subtype];
        int end = start + SUBTYPE_DEFAULT_COUNTS[subtype];
        for (int i = start; i < end; i++) {
            if (!ticks[i]) return false;
        }
        return true;
    }

    static int subtypeForPosition(int position) {
        if (position < 0 || position >= SUBTYPE_BY_POSITION.length) {
            throw new IllegalArgumentException("Invalid pyramid position");
        }
        return SUBTYPE_BY_POSITION[position];
    }

    static PyramidInteractionRules.ExtraDisplay mixedSubtypeExtraDisplay(int extraCount) {
        return PyramidInteractionRules.extraDisplay(extraCount, 1);
    }

    static OverviewSelection selectOverviewDate(LocalDate date, int todayTab) {
        if (date == null) throw new IllegalArgumentException("Missing overview date");
        return new OverviewSelection(date, date.withDayOfMonth(1), todayTab);
    }

    private static boolean[] parseTicks(String bits) {
        if (bits.length() != TILE_COUNT) {
            throw new IllegalArgumentException("Invalid tick length");
        }
        boolean[] ticks = new boolean[TILE_COUNT];
        for (int i = 0; i < bits.length(); i++) {
            char value = bits.charAt(i);
            if (value != '0' && value != '1') {
                throw new IllegalArgumentException("Invalid tick value");
            }
            ticks[i] = value == '1';
        }
        return ticks;
    }

    private static int[] parseIntegers(String serialized, int expectedLength) {
        String[] values = serialized.split(",", -1);
        if (values.length != expectedLength) {
            throw new IllegalArgumentException("Invalid subtype-extra length");
        }
        int[] parsed = new int[expectedLength];
        for (int i = 0; i < values.length; i++) {
            try {
                parsed[i] = Integer.parseInt(values[i]);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid subtype-extra value", ex);
            }
        }
        validateNonNegative(parsed, "subtype extras");
        return parsed;
    }

    private static void validateNonNegative(int[] values, String label) {
        for (int value : values) {
            if (value < 0 || value > MAX_EXTRA_COUNT) {
                throw new IllegalArgumentException("Invalid " + label + " value");
            }
        }
    }

    private static String requireCanonicalDate(String value) {
        try {
            LocalDate parsed = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            if (!value.equals(parsed.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
                throw new IllegalArgumentException("Invalid date");
            }
            return value;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid date", ex);
        }
    }

    private static void requireSubtype(int subtype) {
        if (subtype < 0 || subtype >= SUBTYPE_COUNT) {
            throw new IllegalArgumentException("Invalid subtype");
        }
    }

    private static void requireLength(boolean[] values, int expected, String label) {
        if (values == null || values.length != expected) {
            throw new IllegalArgumentException("Invalid " + label + " length");
        }
    }

    private static void requireLength(int[] values, int expected, String label) {
        if (values == null || values.length != expected) {
            throw new IllegalArgumentException("Invalid " + label + " length");
        }
    }

    private static void appendIntegers(StringBuilder target, int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) target.append(',');
            target.append(values[i]);
        }
    }

    static final class DayState {
        final boolean[] ticks;
        final int[] subtypeExtras;

        DayState(boolean[] ticks, int[] subtypeExtras) {
            this.ticks = ticks;
            this.subtypeExtras = subtypeExtras;
        }
    }

    static final class BackupModel {
        final Map<String, String> dayValues;
        final String selectedDate;

        BackupModel(Map<String, String> dayValues, String selectedDate) {
            this.dayValues = dayValues;
            this.selectedDate = selectedDate;
        }
    }

    static final class OverviewSelection {
        final LocalDate selectedDate;
        final LocalDate overviewMonth;
        final int activeTab;

        OverviewSelection(LocalDate selectedDate, LocalDate overviewMonth, int activeTab) {
            this.selectedDate = selectedDate;
            this.overviewMonth = overviewMonth;
            this.activeTab = activeTab;
        }
    }

    static final class PreferenceTransactionResult {
        final TransactionOutcome outcome;
        final boolean targetCommitReported;
        final boolean rollbackAttempted;
        final boolean originalCommitReported;

        PreferenceTransactionResult(
                TransactionOutcome outcome,
                boolean targetCommitReported,
                boolean rollbackAttempted,
                boolean originalCommitReported) {
            this.outcome = outcome;
            this.targetCommitReported = targetCommitReported;
            this.rollbackAttempted = rollbackAttempted;
            this.originalCommitReported = originalCommitReported;
        }
    }
}

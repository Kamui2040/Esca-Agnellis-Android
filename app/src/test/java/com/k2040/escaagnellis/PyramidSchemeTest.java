package com.k2040.escaagnellis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PyramidSchemeTest {
    @Test
    public void currentContract_usesFreshPreferencesAndBackupSchemaTwo() {
        assertEquals("esca_public_v1", PyramidScheme.PREFERENCES_NAME);
        assertEquals(2, PyramidScheme.BACKUP_SCHEMA_VERSION);
        PyramidScheme.requireCurrentBackupSchema(2);
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.requireCurrentBackupSchema(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.requireCurrentBackupSchema(3));
    }

    @Test
    public void genericBackupPreferenceAllowlist_isEmpty() {
        assertTrue(PyramidScheme.currentGenericBackupPreferenceKeys().isEmpty());
    }

    @Test
    public void genericBackupPreferences_acceptEmptyKeySet() {
        PyramidScheme.requireCurrentGenericBackupPreferenceKeys(Collections.emptySet());
    }

    @Test
    public void genericBackupPreferences_rejectThemeMode() {
        assertGenericBackupPreferenceRejected("theme_mode");
    }

    @Test
    public void genericBackupPreferences_rejectSkinStyle() {
        assertGenericBackupPreferenceRejected("skin_style");
    }

    @Test
    public void genericBackupPreferences_rejectLegacySnakeCaseSchemeVersion() {
        assertGenericBackupPreferenceRejected("pyramid_scheme_version");
    }

    @Test
    public void genericBackupPreferences_rejectLegacyCamelCaseSchemeVersion() {
        assertGenericBackupPreferenceRejected("pyramidSchemeVersion");
    }

    @Test
    public void genericBackupPreferences_rejectLegacyProduceKeys() {
        assertGenericBackupPreferenceRejected("fruitVegetableExtras");
        assertGenericBackupPreferenceRejected("fruitVegetableSplit");
    }

    @Test
    public void genericBackupPreferences_rejectUnknownKey() {
        assertGenericBackupPreferenceRejected("unexpected_setting");
    }

    @Test
    public void groups_haveStableIdentifiersAndTargets() {
        assertArrayEquals(
                new String[] {
                        "extras",
                        "oils_fats",
                        "nuts_seeds",
                        "milk_dairy",
                        "legumes_meat_fish_egg",
                        "bread_grains_sides",
                        "fruit_vegetables",
                        "drinks"
                },
                PyramidScheme.GROUP_IDS);
        assertArrayEquals(
                new int[] { 1, 2, 1, 2, 1, 4, 5, 6 },
                PyramidScheme.GROUP_TARGETS);
    }

    @Test
    public void subtypeLayout_matchesSixPyramidRows() {
        assertArrayEquals(
                new int[] { 1, 2, 1, 2, 1, 3, 1, 5, 6 },
                PyramidScheme.SUBTYPE_DEFAULT_COUNTS);
        assertEquals(PyramidScheme.SUBTYPE_OILS_FATS, PyramidScheme.subtypeForPosition(1));
        assertEquals(PyramidScheme.SUBTYPE_OILS_FATS, PyramidScheme.subtypeForPosition(2));
        assertEquals(PyramidScheme.SUBTYPE_NUTS_SEEDS, PyramidScheme.subtypeForPosition(3));
        assertEquals(PyramidScheme.SUBTYPE_GRAINS, PyramidScheme.subtypeForPosition(9));
        assertEquals(PyramidScheme.SUBTYPE_SIDES, PyramidScheme.subtypeForPosition(10));
        for (int position = 11; position <= 15; position++) {
            assertEquals(
                    PyramidScheme.SUBTYPE_PRODUCE,
                    PyramidScheme.subtypeForPosition(position));
        }
    }

    @Test
    public void currentDay_roundTripsWithoutLegacyFields() {
        boolean[] ticks = new boolean[PyramidScheme.TILE_COUNT];
        ticks[2] = true;
        ticks[11] = true;
        int[] subtypeExtras = new int[] { 0, 2, 1, 0, 3, 0, 4, 5, 6 };

        String serialized = PyramidScheme.serialize(
                new PyramidScheme.DayState(ticks, subtypeExtras));
        PyramidScheme.DayState parsed = PyramidScheme.parseStoredDay(serialized);

        assertEquals(2, serialized.split("\\|", -1).length);
        assertArrayEquals(ticks, parsed.ticks);
        assertArrayEquals(subtypeExtras, parsed.subtypeExtras);
        assertEquals(serialized, PyramidScheme.serialize(parsed));
    }

    @Test
    public void currentDay_rejectsMalformedOrLegacyShapes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.parseStoredDay("000|0,0,0,0,0,0,0,0,0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.parseStoredDay(
                        "0000000000000000000000|0,0,0,0,0,0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.parseStoredDay(
                        "0000000000000000000000|0,0,0,0,0,0,0,0,0|0,0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.parseStoredDay(
                        "0000000000000000000000|0,0,-1,0,0,0,0,0,0"));
    }

    @Test
    public void backupArrays_areValidatedAndDefensivelyCopied() {
        boolean[] ticks = new boolean[PyramidScheme.TILE_COUNT];
        int[] subtypeExtras = new int[PyramidScheme.SUBTYPE_COUNT];

        PyramidScheme.DayState state =
                PyramidScheme.fromBackupArrays(ticks, subtypeExtras);
        ticks[0] = true;
        subtypeExtras[0] = 9;

        assertFalse(state.ticks[0]);
        assertEquals(0, state.subtypeExtras[0]);
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.fromBackupArrays(
                        new boolean[PyramidScheme.TILE_COUNT],
                        new int[PyramidScheme.SUBTYPE_COUNT - 1]));
    }

    @Test
    public void mixedRowSubtypes_fillIndependently() {
        boolean[] ticks = new boolean[PyramidScheme.TILE_COUNT];

        assertTrue(PyramidScheme.fillSubtype(ticks, PyramidScheme.SUBTYPE_OILS_FATS));
        assertTrue(PyramidScheme.fillSubtype(ticks, PyramidScheme.SUBTYPE_OILS_FATS));

        assertTrue(ticks[1]);
        assertTrue(ticks[2]);
        assertFalse(ticks[3]);
        assertTrue(PyramidScheme.isSubtypeComplete(ticks, PyramidScheme.SUBTYPE_OILS_FATS));
        assertFalse(PyramidScheme.isSubtypeComplete(ticks, PyramidScheme.SUBTYPE_NUTS_SEEDS));
    }

    @Test
    public void subtypeRemoval_consumesOnlyItsExtrasThenDefaultsFromRight() {
        boolean[] ticks = new boolean[PyramidScheme.TILE_COUNT];
        ticks[1] = true;
        ticks[2] = true;
        ticks[3] = true;
        int[] subtypeExtras = new int[PyramidScheme.SUBTYPE_COUNT];
        subtypeExtras[PyramidScheme.SUBTYPE_OILS_FATS] = 2;
        subtypeExtras[PyramidScheme.SUBTYPE_NUTS_SEEDS] = 1;

        PyramidScheme.removeSubtype(ticks, subtypeExtras, PyramidScheme.SUBTYPE_OILS_FATS);
        PyramidScheme.removeSubtype(ticks, subtypeExtras, PyramidScheme.SUBTYPE_OILS_FATS);
        PyramidScheme.removeSubtype(ticks, subtypeExtras, PyramidScheme.SUBTYPE_OILS_FATS);

        assertEquals(0, subtypeExtras[PyramidScheme.SUBTYPE_OILS_FATS]);
        assertEquals(1, subtypeExtras[PyramidScheme.SUBTYPE_NUTS_SEEDS]);
        assertTrue(ticks[1]);
        assertFalse(ticks[2]);
        assertTrue(ticks[3]);
    }

    @Test
    public void mixedSubtypeExtras_showOneTileAndOverflowRemainder() {
        PyramidInteractionRules.ExtraDisplay display =
                PyramidScheme.mixedSubtypeExtraDisplay(5);

        assertEquals(1, display.visible);
        assertEquals(4, display.overflow);
    }

    @Test
    public void overviewSelection_entersTodayOnExactDate() {
        LocalDate date = LocalDate.of(2026, 6, 18);

        PyramidScheme.OverviewSelection selection =
                PyramidScheme.selectOverviewDate(date, 1);

        assertEquals(date, selection.selectedDate);
        assertEquals(LocalDate.of(2026, 6, 1), selection.overviewMonth);
        assertEquals(1, selection.activeTab);
    }

    @Test
    public void backupPreparation_validatesEveryDayBeforeReturning() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put(
                "2026-06-18",
                "0000000000000000000000|0,0,0,0,0,0,0,0,0");
        source.put(
                "2026-06-19",
                "invalid|0,0,0,0,0,0,0,0,0");

        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.prepareBackupModel(2, source, "2026-06-18"));
        assertEquals(2, source.size());
    }

    @Test
    public void backupPreparation_returnsCanonicalCurrentModel() {
        Map<String, String> source = new LinkedHashMap<>();
        String day = "1000000000000000000000|0,1,0,0,0,0,0,2,0";
        source.put("2026-06-18", day);

        PyramidScheme.BackupModel model =
                PyramidScheme.prepareBackupModel(2, source, "2026-06-19");

        assertEquals("2026-06-19", model.selectedDate);
        assertEquals(day, model.dayValues.get("2026-06-18"));
    }

    @Test
    public void preferenceSnapshotCopy_isExactAndDefensiveForSets() {
        Set<String> originalSet = new HashSet<>();
        originalSet.add("alpha");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("string", "value");
        source.put("boolean", true);
        source.put("int", 7);
        source.put("long", 8L);
        source.put("float", 1.5f);
        source.put("set", originalSet);

        Map<String, Object> copy = PyramidScheme.copyPreferenceSnapshot(source);
        assertTrue(PyramidScheme.preferenceSnapshotsEqual(source, copy));
        originalSet.add("changed-after-copy");

        assertFalse(((Set<?>) copy.get("set")).contains("changed-after-copy"));
        assertFalse(PyramidScheme.preferenceSnapshotsEqual(source, copy));
    }

    @Test
    public void preferenceTransaction_targetSnapshotIsAuthoritative() {
        Map<String, Object> original = snapshot("state", "original");
        Map<String, Object> target = snapshot("state", "target");

        PyramidScheme.PreferenceTransactionResult result =
                PyramidScheme.classifyPreferenceTransaction(
                        false,
                        target,
                        target,
                        false,
                        false,
                        original,
                        null);

        assertEquals(PyramidScheme.TransactionOutcome.TARGET_APPLIED, result.outcome);
        assertFalse(result.targetCommitReported);
        assertFalse(result.rollbackAttempted);
    }

    @Test
    public void preferenceTransaction_classifiesVerifiedRollback() {
        Map<String, Object> original = snapshot("state", "original");
        Map<String, Object> target = snapshot("state", "target");
        Map<String, Object> mixed = snapshot("state", "mixed");

        PyramidScheme.PreferenceTransactionResult result =
                PyramidScheme.classifyPreferenceTransaction(
                        false,
                        target,
                        mixed,
                        true,
                        false,
                        original,
                        original);

        assertEquals(PyramidScheme.TransactionOutcome.ORIGINAL_RESTORED, result.outcome);
        assertTrue(result.rollbackAttempted);
        assertFalse(result.originalCommitReported);
    }

    @Test
    public void preferenceTransaction_classifiesUnverifiedStateAsIndeterminate() {
        Map<String, Object> original = snapshot("state", "original");
        Map<String, Object> target = snapshot("state", "target");
        Map<String, Object> mixed = snapshot("state", "mixed");

        PyramidScheme.PreferenceTransactionResult result =
                PyramidScheme.classifyPreferenceTransaction(
                        true,
                        target,
                        mixed,
                        true,
                        true,
                        original,
                        mixed);

        assertEquals(PyramidScheme.TransactionOutcome.INDETERMINATE, result.outcome);
        assertTrue(result.targetCommitReported);
        assertTrue(result.originalCommitReported);
    }

    private static Map<String, Object> snapshot(String key, Object value) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(key, value);
        return snapshot;
    }

    private static void assertGenericBackupPreferenceRejected(String key) {
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidScheme.requireCurrentGenericBackupPreferenceKeys(
                        Collections.singleton(key)));
    }
}

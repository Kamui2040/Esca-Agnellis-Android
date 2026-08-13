package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CompanionBackupCodecTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 7, 2);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 7, 3);

    @Test
    public void roundTripPreservesAllDurableCompanionState() {
        CompanionState state = fullState();

        Map<String, Object> encoded = CompanionBackupCodec.encode(state);
        CompanionState decoded = CompanionBackupCodec.decode(encoded);

        assertEquals(state, decoded);
        assertEquals(
                CompanionBackupCodec.SCHEMA_NAME,
                encoded.get(CompanionBackupCodec.KEY_SCHEMA_NAME));
        assertEquals(
                CompanionBackupCodec.SCHEMA_VERSION,
                encoded.get(CompanionBackupCodec.KEY_SCHEMA_VERSION));
        assertEquals(
                CompanionState.STORAGE_SCHEMA_VERSION,
                encoded.get(
                        CompanionBackupCodec.KEY_COMPANION_STORAGE_SCHEMA_VERSION));
    }

    @Test
    public void encodingDoesNotPersistCompanionName() {
        CompanionState state = fullState().withCompanionName("Luna");

        Map<String, Object> encoded = CompanionBackupCodec.encode(state);
        Map<String, Object> encodedState = stateMap(encoded);

        assertFalse(encodedState.containsKey(CompanionStateCodec.KEY_COMPANION_NAME));
        assertEquals("Luna", state.companionName);
        assertEquals(null, CompanionBackupCodec.decode(encoded).companionName);
    }

    @Test
    public void encodingUsesCanonicalDateAndPositionOrder() {
        Map<String, Object> encoded = CompanionBackupCodec.encode(fullState());
        Map<String, Object> state = stateMap(encoded);
        List<?> ledger = (List<?>) state.get(CompanionBackupCodec.KEY_REWARD_LEDGER);
        List<?> deadlines =
                (List<?>) state.get(
                        CompanionBackupCodec.KEY_REWARD_GRACE_DEADLINES);

        assertEquals(2, ledger.size());
        assertEquals(
                FIRST_DATE.toString(),
                entry(ledger, 0).get(CompanionBackupCodec.KEY_DATE));
        assertEquals(
                SECOND_DATE.toString(),
                entry(ledger, 1).get(CompanionBackupCodec.KEY_DATE));

        assertEquals(2, deadlines.size());
        assertEquals(
                FIRST_DATE.toString(),
                entry(deadlines, 0).get(CompanionBackupCodec.KEY_DATE));
        assertEquals(
                1,
                entry(deadlines, 0).get(CompanionBackupCodec.KEY_POSITION));
        assertEquals(
                SECOND_DATE.toString(),
                entry(deadlines, 1).get(CompanionBackupCodec.KEY_DATE));
        assertEquals(
                2,
                entry(deadlines, 1).get(CompanionBackupCodec.KEY_POSITION));
    }

    @Test
    public void primaryTrackingBackupIsRejectedAsWrongFormat() {
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put(CompanionBackupCodec.KEY_SCHEMA_NAME, "esca-agnellis-export");

        expectThrows(
                CompanionBackupCodec.WrongFormatException.class,
                () -> CompanionBackupCodec.decode(primary));
    }

    @Test
    public void unsupportedBackupAndStateSchemasAreRejectedSeparately() {
        Map<String, Object> unsupportedBackup =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        unsupportedBackup.put(CompanionBackupCodec.KEY_SCHEMA_VERSION, 99);
        expectThrows(
                CompanionBackupCodec.UnsupportedSchemaException.class,
                () -> CompanionBackupCodec.decode(unsupportedBackup));

        Map<String, Object> unsupportedState =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        unsupportedState.put(
                CompanionBackupCodec.KEY_COMPANION_STORAGE_SCHEMA_VERSION,
                99);
        expectThrows(
                CompanionBackupCodec.UnsupportedStateSchemaException.class,
                () -> CompanionBackupCodec.decode(unsupportedState));
    }

    @Test
    public void unknownOrMissingFieldsAreRejected() {
        Map<String, Object> unknownRoot =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        unknownRoot.put("unexpected", true);
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(unknownRoot));

        Map<String, Object> missingRoot =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        missingRoot.remove(CompanionBackupCodec.KEY_STATE);
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(missingRoot));

        Map<String, Object> unknownState =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        stateMap(unknownState).put("unexpected", true);
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(unknownState));
    }

    @Test
    public void nonCanonicalLongMaskAndDateValuesAreRejected() {
        Map<String, Object> balance =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        stateMap(balance).put(CompanionBackupCodec.KEY_BALANCE, "03");
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(balance));

        Map<String, Object> mask =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        entry(
                (List<?>) stateMap(mask).get(
                        CompanionBackupCodec.KEY_REWARD_LEDGER),
                0).put(CompanionBackupCodec.KEY_REWARD_MASK, "03");
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(mask));

        Map<String, Object> date =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        entry(
                (List<?>) stateMap(date).get(
                        CompanionBackupCodec.KEY_REWARD_LEDGER),
                0).put(CompanionBackupCodec.KEY_DATE, "2026-7-2");
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(date));
    }

    @Test
    public void nonCanonicalEntryOrderAndDuplicatesAreRejected() {
        Map<String, Object> reversed =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        List<Object> ledger = list(
                stateMap(reversed),
                CompanionBackupCodec.KEY_REWARD_LEDGER);
        Object first = ledger.remove(0);
        ledger.add(first);
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(reversed));

        Map<String, Object> duplicate =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        List<Object> duplicateLedger = list(
                stateMap(duplicate),
                CompanionBackupCodec.KEY_REWARD_LEDGER);
        duplicateLedger.add(new LinkedHashMap<>(entry(duplicateLedger, 0)));
        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(duplicate));
    }

    @Test
    public void deadlineWithoutMatchingRewardIsRejected() {
        Map<String, Object> encoded =
                mutableCopy(CompanionBackupCodec.encode(fullState()));
        list(
                stateMap(encoded),
                CompanionBackupCodec.KEY_REWARD_LEDGER).clear();

        expectThrows(
                IllegalArgumentException.class,
                () -> CompanionBackupCodec.decode(encoded));
    }

    private static CompanionState fullState() {
        return CompanionState.disabledDefault()
                .withEnabled(true)
                .withReducedAnimation(true)
                .withReward(SECOND_DATE, 2, 1L)
                .withReward(FIRST_DATE, 1, 1L)
                .withReward(FIRST_DATE, 0, 1L)
                .withRewardGraceDeadline(FIRST_DATE, 1, 20_000L)
                .withRewardGraceDeadline(SECOND_DATE, 2, 30_000L)
                .withPurchase("feed_apple", 1L, 5_000L);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stateMap(Map<String, Object> document) {
        return (Map<String, Object>) document.get(CompanionBackupCodec.KEY_STATE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entry(List<?> values, int index) {
        return (Map<String, Object>) values.get(index);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> values, String key) {
        return (List<Object>) values.get(key);
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
        return value;
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> type,
            Runnable action) {
        try {
            action.run();
        } catch (Throwable thrown) {
            assertTrue(
                    "Expected " + type.getName() + " but got " + thrown,
                    type.isInstance(thrown));
            return type.cast(thrown);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}

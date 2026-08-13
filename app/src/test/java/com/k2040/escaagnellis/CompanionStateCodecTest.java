package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class CompanionStateCodecTest {
    @Test
    public void roundTripPreservesDurableStateAndGraceDeadlines() {
        CompanionEconomy economy = CompanionEconomy.defaultEconomy();
        LocalDate firstDate = LocalDate.of(2026, 7, 1);
        LocalDate secondDate = LocalDate.of(2026, 7, 2);
        CompanionState state = CompanionState.disabledDefault()
                .withEnabled(true)
                .withCompanionName("  Luna   Star  ")
                .withReducedAnimation(true);
        state = economy.rewardDefaultPosition(state, firstDate, 0).state
                .withRewardGraceDeadline(firstDate, 0, 16_000L);
        state = economy.rewardDefaultPosition(state, secondDate, 16).state
                .withRewardGraceDeadline(secondDate, 16, 20_000L);
        state = economy.rewardDefaultPosition(state, secondDate.plusDays(1), 1).state;
        state = economy.purchaseInteraction(
                state,
                "feed_apple",
                1L,
                Instant.parse("2026-07-02T12:00:00Z")).state;

        Map<String, Object> encoded = CompanionStateCodec.encode(state);
        CompanionState decoded = CompanionStateCodec.decode(encoded);

        assertEquals(state, decoded);
        assertEquals(10, encoded.size());
        assertEquals("Luna Star", decoded.companionName);
        assertFalse(encoded.containsKey("pose"));
        assertFalse(encoded.containsKey("animation_frame"));
        assertFalse(encoded.containsKey("canvas_x"));
    }

    @Test
    public void roundTripWithoutNameStillDecodesAsNamelessState() {
        CompanionState state = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(LocalDate.of(2026, 7, 1), 0, 1L);

        Map<String, Object> encoded = CompanionStateCodec.encode(state);
        CompanionState decoded = CompanionStateCodec.decode(encoded);

        assertEquals(state, decoded);
        assertEquals(9, encoded.size());
        assertFalse(encoded.containsKey(CompanionStateCodec.KEY_COMPANION_NAME));
        assertEquals(null, decoded.companionName);
    }

    @Test
    public void legacySchemaDecodesAsCurrentStateWithoutDeadlines() {
        CompanionState state = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(LocalDate.of(2026, 7, 1), 0, 1L);
        Map<String, Object> legacy = new LinkedHashMap<>(CompanionStateCodec.encode(state));
        legacy.put(
                CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION,
                CompanionState.LEGACY_STORAGE_SCHEMA_VERSION);
        legacy.remove(CompanionStateCodec.KEY_REWARD_GRACE_DEADLINES);
        legacy.remove(CompanionStateCodec.KEY_COMPANION_NAME);

        CompanionStateCodec.DecodeResult decoded = CompanionStateCodec.decodeResult(legacy);

        assertTrue(decoded.migrated);
        assertEquals(CompanionState.STORAGE_SCHEMA_VERSION, decoded.state.storageSchemaVersion);
        assertEquals(1L, decoded.state.balance);
        assertTrue(decoded.state.hasReward(LocalDate.of(2026, 7, 1), 0));
        assertTrue(decoded.state.rewardGraceDeadlines.isEmpty());
        assertEquals(null, decoded.state.companionName);
    }

    @Test
    public void decodeRejectsUnsupportedOrMalformedState() {
        Map<String, Object> values = CompanionStateCodec.encode(CompanionState.disabledDefault());

        Map<String, Object> unsupported = new LinkedHashMap<>(values);
        unsupported.put(CompanionStateCodec.KEY_STORAGE_SCHEMA_VERSION, 3);
        assertThrows(
                CompanionStateCodec.UnsupportedSchemaException.class,
                () -> CompanionStateCodec.decode(unsupported));

        Map<String, Object> negativeBalance = new LinkedHashMap<>(values);
        negativeBalance.put(CompanionStateCodec.KEY_BALANCE, -1L);
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionStateCodec.decode(negativeBalance));

        Map<String, Object> missingLedger = new LinkedHashMap<>(values);
        missingLedger.remove(CompanionStateCodec.KEY_REWARD_LEDGER);
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionStateCodec.decode(missingLedger));

        CompanionState rewarded = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(LocalDate.of(2026, 7, 1), 0, 1L)
                .withRewardGraceDeadline(LocalDate.of(2026, 7, 1), 0, 16_000L);
        Map<String, Object> malformedDeadline = new LinkedHashMap<>(
                CompanionStateCodec.encode(rewarded));
        malformedDeadline.put(
                CompanionStateCodec.KEY_REWARD_GRACE_DEADLINES,
                java.util.Collections.singleton("2026-07-01#00#16000"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionStateCodec.decode(malformedDeadline));
    }

    @Test
    public void ledgersUseCanonicalCompactEntries() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        CompanionState state = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(date, 0, 1L)
                .withReward(date, 16, 1L)
                .withRewardGraceDeadline(date, 16, 20_000L);
        Map<String, Object> encoded = CompanionStateCodec.encode(state);

        Object ledger = encoded.get(CompanionStateCodec.KEY_REWARD_LEDGER);
        Object deadlines = encoded.get(CompanionStateCodec.KEY_REWARD_GRACE_DEADLINES);
        assertTrue(ledger.toString().contains("2026-07-01#10001"));
        assertTrue(deadlines.toString().contains("2026-07-01#16#20000"));
    }
}

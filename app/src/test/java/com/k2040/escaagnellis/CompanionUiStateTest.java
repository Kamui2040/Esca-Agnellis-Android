package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

public class CompanionUiStateTest {
    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    public void emptyStateIsAvailableAndDisabled() {
        CompanionUiState.ViewState state = CompanionUiState.fromLoad(
                new CompanionRepository.LoadResult(
                        CompanionRepository.LoadStatus.EMPTY,
                        CompanionState.disabledDefault()),
                NOW);

        assertTrue(state.available);
        assertFalse(state.presentation.enabled);
        assertEquals(0L, state.presentation.balance);
    }

    @Test
    public void loadedEnabledStateUsesPresentationSnapshot() {
        CompanionState enabled = CompanionState.restore(
                CompanionState.STORAGE_SCHEMA_VERSION,
                true,
                7L,
                CompanionState.DEFAULT_COMPANION_ID,
                "Luna",
                false,
                null,
                CompanionState.NO_INTERACTION_TIME,
                Collections.emptyMap(),
                Collections.emptyMap());

        CompanionUiState.ViewState state = CompanionUiState.fromLoad(
                new CompanionRepository.LoadResult(
                        CompanionRepository.LoadStatus.LOADED,
                        enabled),
                NOW);

        assertTrue(state.available);
        assertTrue(state.presentation.enabled);
        assertEquals(7L, state.presentation.balance);
        assertEquals("Luna", state.presentation.displayName);
        assertEquals(CompanionPresentation.POSE_IDLE, state.presentation.poseId);
    }

    @Test
    public void corruptOrMissingLoadBecomesUnavailableDisabledPresentation() {
        CompanionUiState.ViewState corrupt = CompanionUiState.fromLoad(
                new CompanionRepository.LoadResult(
                        CompanionRepository.LoadStatus.CORRUPT,
                        CompanionState.disabledDefault()),
                NOW);
        CompanionUiState.ViewState missing = CompanionUiState.fromLoad(null, NOW);

        assertFalse(corrupt.available);
        assertFalse(corrupt.presentation.enabled);
        assertFalse(missing.available);
        assertFalse(missing.presentation.enabled);
    }

    @Test
    public void appliedAndMatchingNoChangeClassifyAsSuccessful() {
        CompanionRepository.MutationResult enabled =
                new CompanionRepository.MutationResult(
                        CompanionRepository.MutationStatus.APPLIED,
                        CompanionState.disabledDefault().withEnabled(true),
                        null,
                        CompanionRepository.LoadStatus.LOADED);
        CompanionRepository.MutationResult disabled =
                CompanionRepository.MutationResult.noChange(
                        CompanionState.disabledDefault(),
                        null,
                        CompanionRepository.LoadStatus.LOADED);

        assertEquals(
                CompanionUiState.ToggleOutcome.ENABLED,
                CompanionUiState.classifyToggle(enabled, true));
        assertEquals(
                CompanionUiState.ToggleOutcome.DISABLED,
                CompanionUiState.classifyToggle(disabled, false));
    }

    @Test
    public void unavailableIndeterminateRestoredOrMismatchedResultsFailClosed() {
        CompanionState enabled = CompanionState.disabledDefault().withEnabled(true);

        CompanionRepository.MutationResult unavailable =
                new CompanionRepository.MutationResult(
                        CompanionRepository.MutationStatus.UNAVAILABLE,
                        CompanionState.disabledDefault(),
                        null,
                        CompanionRepository.LoadStatus.CORRUPT);
        CompanionRepository.MutationResult indeterminate =
                new CompanionRepository.MutationResult(
                        CompanionRepository.MutationStatus.INDETERMINATE,
                        CompanionState.disabledDefault(),
                        null,
                        null);
        CompanionRepository.MutationResult restored =
                new CompanionRepository.MutationResult(
                        CompanionRepository.MutationStatus.ORIGINAL_RESTORED,
                        CompanionState.disabledDefault(),
                        null,
                        CompanionRepository.LoadStatus.LOADED);
        CompanionRepository.MutationResult mismatched =
                new CompanionRepository.MutationResult(
                        CompanionRepository.MutationStatus.APPLIED,
                        enabled,
                        null,
                        CompanionRepository.LoadStatus.LOADED);

        assertEquals(
                CompanionUiState.ToggleOutcome.UNAVAILABLE,
                CompanionUiState.classifyToggle(unavailable, true));
        assertEquals(
                CompanionUiState.ToggleOutcome.UNAVAILABLE,
                CompanionUiState.classifyToggle(indeterminate, true));
        assertEquals(
                CompanionUiState.ToggleOutcome.UNAVAILABLE,
                CompanionUiState.classifyToggle(restored, true));
        assertEquals(
                CompanionUiState.ToggleOutcome.UNAVAILABLE,
                CompanionUiState.classifyToggle(mismatched, false));
        assertEquals(
                CompanionUiState.ToggleOutcome.UNAVAILABLE,
                CompanionUiState.classifyToggle(null, true));
    }

    @Test
    public void purchaseResultsClassifySuccessInsufficientAndUnavailable() {
        CompanionState enabled = CompanionState.disabledDefault()
                .withEnabled(true)
                .withReward(java.time.LocalDate.of(2026, 7, 1), 0, 10L);

        CompanionRepository.MutationResult success =
                new CompanionRepository.MutationResult(
                        CompanionRepository.MutationStatus.APPLIED,
                        enabled.withPurchase("play", 4L, 5_000L),
                        CompanionEconomy.Outcome.PURCHASE_COMPLETED,
                        CompanionRepository.LoadStatus.LOADED);
        CompanionRepository.MutationResult insufficient =
                CompanionRepository.MutationResult.noChange(
                        enabled,
                        CompanionEconomy.Outcome.INSUFFICIENT_BALANCE,
                        CompanionRepository.LoadStatus.LOADED);
        CompanionRepository.MutationResult disabled =
                CompanionRepository.MutationResult.noChange(
                        CompanionState.disabledDefault(),
                        CompanionEconomy.Outcome.DISABLED,
                        CompanionRepository.LoadStatus.EMPTY);
        CompanionRepository.MutationResult restored =
                new CompanionRepository.MutationResult(
                        CompanionRepository.MutationStatus.ORIGINAL_RESTORED,
                        enabled,
                        CompanionEconomy.Outcome.PURCHASE_COMPLETED,
                        CompanionRepository.LoadStatus.LOADED);

        assertEquals(
                CompanionUiState.InteractionOutcome.SUCCESS,
                CompanionUiState.classifyInteractionPurchase(success));
        assertEquals(
                CompanionUiState.InteractionOutcome.INSUFFICIENT_BALANCE,
                CompanionUiState.classifyInteractionPurchase(insufficient));
        assertEquals(
                CompanionUiState.InteractionOutcome.UNAVAILABLE,
                CompanionUiState.classifyInteractionPurchase(disabled));
        assertEquals(
                CompanionUiState.InteractionOutcome.UNAVAILABLE,
                CompanionUiState.classifyInteractionPurchase(restored));
        assertEquals(
                CompanionUiState.InteractionOutcome.UNAVAILABLE,
                CompanionUiState.classifyInteractionPurchase(null));
    }
}

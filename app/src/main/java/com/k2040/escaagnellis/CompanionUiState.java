package com.k2040.escaagnellis;

import java.time.Instant;

final class CompanionUiState {
    private CompanionUiState() {
    }

    static ViewState fromLoad(
            CompanionRepository.LoadResult loaded,
            Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Missing companion UI timestamp");
        }

        boolean available = loaded != null && loaded.isUsable();
        CompanionState state = available
                ? loaded.state
                : CompanionState.disabledDefault();
        return new ViewState(
                available,
                CompanionPresentation.derive(state, now));
    }

    static ToggleOutcome classifyToggle(
            CompanionRepository.MutationResult result,
            boolean requestedEnabled) {
        if (result == null
                || result.state == null
                || (result.status != CompanionRepository.MutationStatus.APPLIED
                && result.status != CompanionRepository.MutationStatus.NO_CHANGE)
                || result.state.enabled != requestedEnabled) {
            return ToggleOutcome.UNAVAILABLE;
        }
        return requestedEnabled ? ToggleOutcome.ENABLED : ToggleOutcome.DISABLED;
    }

    static InteractionOutcome classifyInteractionPurchase(
            CompanionRepository.MutationResult result) {
        if (result == null
                || result.state == null
                || result.economyOutcome == null) {
            return InteractionOutcome.UNAVAILABLE;
        }
        if (result.status == CompanionRepository.MutationStatus.APPLIED
                && result.economyOutcome == CompanionEconomy.Outcome.PURCHASE_COMPLETED) {
            return InteractionOutcome.SUCCESS;
        }
        if ((result.status == CompanionRepository.MutationStatus.APPLIED
                || result.status == CompanionRepository.MutationStatus.NO_CHANGE)
                && result.economyOutcome == CompanionEconomy.Outcome.INSUFFICIENT_BALANCE) {
            return InteractionOutcome.INSUFFICIENT_BALANCE;
        }
        return InteractionOutcome.UNAVAILABLE;
    }

    enum ToggleOutcome {
        ENABLED,
        DISABLED,
        UNAVAILABLE
    }

    enum InteractionOutcome {
        SUCCESS,
        INSUFFICIENT_BALANCE,
        UNAVAILABLE
    }

    static final class ViewState {
        final boolean available;
        final CompanionPresentation.Snapshot presentation;

        ViewState(
                boolean available,
                CompanionPresentation.Snapshot presentation) {
            if (presentation == null) {
                throw new IllegalArgumentException("Missing companion presentation");
            }
            this.available = available;
            this.presentation = presentation;
        }
    }
}

package com.k2040.escaagnellis;

import java.time.Instant;
import java.time.LocalDate;

final class CompanionEconomy {
    static final long DEFAULT_REWARD_PER_POSITION = 1L;

    private final long rewardPerDefaultPosition;

    CompanionEconomy(long rewardPerDefaultPosition) {
        if (rewardPerDefaultPosition <= 0L) {
            throw new IllegalArgumentException("Reward amount must be positive");
        }
        this.rewardPerDefaultPosition = rewardPerDefaultPosition;
    }

    static CompanionEconomy defaultEconomy() {
        return new CompanionEconomy(DEFAULT_REWARD_PER_POSITION);
    }

    long rewardPerDefaultPosition() {
        return rewardPerDefaultPosition;
    }

    Result rewardDefaultPosition(CompanionState state, LocalDate date, int position) {
        requireState(state);
        boolean alreadyRewarded = state.hasReward(date, position);
        if (!state.enabled) {
            return new Result(state, Outcome.DISABLED, 0L);
        }
        if (alreadyRewarded) {
            return new Result(state, Outcome.ALREADY_REWARDED, 0L);
        }
        CompanionState updated = state.withReward(date, position, rewardPerDefaultPosition);
        return new Result(updated, Outcome.REWARD_GRANTED, rewardPerDefaultPosition);
    }

    Result revokeDefaultPosition(CompanionState state, LocalDate date, int position) {
        requireState(state);
        if (!state.hasReward(date, position)) {
            return new Result(state, Outcome.REWARD_NOT_FOUND, 0L);
        }
        if (state.balance < rewardPerDefaultPosition) {
            return new Result(state, Outcome.INSUFFICIENT_BALANCE, 0L);
        }
        CompanionState updated = state.withoutReward(
                date,
                position,
                rewardPerDefaultPosition);
        return new Result(updated, Outcome.REWARD_REVOKED, -rewardPerDefaultPosition);
    }

    Result purchaseInteraction(
            CompanionState state,
            String interactionId,
            long cost,
            Instant occurredAt) {
        return purchaseInteraction(state, interactionId, cost, occurredAt, 0L);
    }

    Result purchaseInteraction(
            CompanionState state,
            String interactionId,
            long cost,
            Instant occurredAt,
            long minimumRetainedBalance) {
        requireState(state);
        CompanionState.requireStableId(interactionId, "interaction id");
        if (cost <= 0L) {
            throw new IllegalArgumentException("Interaction cost must be positive");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Missing interaction timestamp");
        }
        if (minimumRetainedBalance < 0L || minimumRetainedBalance > state.balance) {
            throw new IllegalArgumentException("Invalid retained companion balance");
        }
        long occurredAtEpochMillis = occurredAt.toEpochMilli();
        if (occurredAtEpochMillis <= CompanionState.NO_INTERACTION_TIME) {
            throw new IllegalArgumentException("Interaction timestamp must be positive");
        }
        if (!state.enabled) {
            return new Result(state, Outcome.DISABLED, 0L);
        }
        if (cost > state.balance - minimumRetainedBalance) {
            return new Result(state, Outcome.INSUFFICIENT_BALANCE, 0L);
        }
        CompanionState updated = state.withPurchase(interactionId, cost, occurredAtEpochMillis);
        return new Result(updated, Outcome.PURCHASE_COMPLETED, -cost);
    }

    private static void requireState(CompanionState state) {
        if (state == null) {
            throw new IllegalArgumentException("Missing companion state");
        }
    }

    enum Outcome {
        DISABLED,
        REWARD_GRANTED,
        ALREADY_REWARDED,
        REWARD_REVOKED,
        REWARD_NOT_FOUND,
        REWARD_GRACE_EXPIRED,
        REWARD_GRACE_NOT_FOUND,
        PURCHASE_COMPLETED,
        INSUFFICIENT_BALANCE
    }

    static final class Result {
        final CompanionState state;
        final Outcome outcome;
        final long balanceDelta;

        Result(CompanionState state, Outcome outcome, long balanceDelta) {
            this.state = state;
            this.outcome = outcome;
            this.balanceDelta = balanceDelta;
        }

        boolean changed() {
            return balanceDelta != 0L;
        }
    }
}

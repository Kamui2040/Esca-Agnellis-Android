package com.k2040.escaagnellis;

final class CompanionAffordability {
    private CompanionAffordability() { }

    static boolean isAffordable(long balance, long cost) {
        if (balance < 0L || cost <= 0L) {
            throw new IllegalArgumentException("Invalid balance or interaction cost");
        }
        return balance >= cost;
    }
}

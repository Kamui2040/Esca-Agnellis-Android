package com.k2040.escaagnellis;

/** Local-only scene selection; deliberately independent from the application theme. */
final class CompanionVisualMode {
    enum Mode { DAY, NIGHT }

    private CompanionVisualMode() { }

    static Mode forLocalHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Local hour must be in 0..23");
        }
        return hour >= 6 && hour < 18 ? Mode.DAY : Mode.NIGHT;
    }
}

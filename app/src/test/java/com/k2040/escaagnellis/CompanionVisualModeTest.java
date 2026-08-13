package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CompanionVisualModeTest {
    @Test
    public void localHourUsesInclusiveDayBoundaries() {
        assertEquals(CompanionVisualMode.Mode.NIGHT, CompanionVisualMode.forLocalHour(5));
        assertEquals(CompanionVisualMode.Mode.DAY, CompanionVisualMode.forLocalHour(6));
        assertEquals(CompanionVisualMode.Mode.DAY, CompanionVisualMode.forLocalHour(17));
        assertEquals(CompanionVisualMode.Mode.NIGHT, CompanionVisualMode.forLocalHour(18));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidHour() {
        CompanionVisualMode.forLocalHour(24);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeHour() {
        CompanionVisualMode.forLocalHour(-1);
    }
}

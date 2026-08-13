package com.k2040.escaagnellis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class PyramidReportSourceTest {
    @Test
    public void source_readsOnlyStoredDaysInsideSelectedRange() {
        Map<String, Object> preferences = new LinkedHashMap<>();
        String first = "1000000000000000000000|0,0,0,0,0,0,0,0,0";
        String outside = "0100000000000000000000|0,0,0,0,0,0,0,0,0";
        preferences.put("day_2026-06-01", first);
        preferences.put("day_2026-06-10", outside);
        preferences.put("theme_mode", 2);

        Map<LocalDate, PyramidScheme.DayState> result =
                PyramidReportSource.fromPreferences(
                        preferences,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3));

        assertEquals(1, result.size());
        assertArrayEquals(
                PyramidScheme.parseStoredDay(first).ticks,
                result.get(LocalDate.of(2026, 6, 1)).ticks);
    }

    @Test
    public void source_rejectsMalformedInRangeDay() {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("day_2026-06-02", "invalid");

        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidReportSource.fromPreferences(
                        preferences,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3)));
    }

    @Test
    public void source_ignoresMalformedDayOutsideSelectedRange() {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("day_2026-07-01", "invalid");

        Map<LocalDate, PyramidScheme.DayState> result =
                PyramidReportSource.fromPreferences(
                        preferences,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3));

        assertEquals(0, result.size());
    }

    @Test
    public void source_rejectsNonStringInRangeDayAndInvalidRange() {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("day_2026-06-02", 42);

        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidReportSource.fromPreferences(
                        preferences,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidReportSource.fromPreferences(
                        preferences,
                        LocalDate.of(2026, 6, 3),
                        LocalDate.of(2026, 6, 1)));
    }
}

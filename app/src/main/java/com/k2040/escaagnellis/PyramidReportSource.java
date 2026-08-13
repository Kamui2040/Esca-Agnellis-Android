package com.k2040.escaagnellis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

final class PyramidReportSource {
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PyramidReportSource() {
    }

    static Map<LocalDate, PyramidScheme.DayState> fromPreferences(
            Map<String, ?> preferences,
            LocalDate startDate,
            LocalDate endDate) {
        if (preferences == null) {
            throw new IllegalArgumentException("Missing preferences");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Missing report date range");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Report start date is after end date");
        }

        Map<LocalDate, PyramidScheme.DayState> days = new LinkedHashMap<>();
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            String key = "day_" + date.format(KEY_FORMAT);
            Object stored = preferences.get(key);
            if (stored != null) {
                if (!(stored instanceof String)) {
                    throw new IllegalArgumentException("Invalid stored day value");
                }
                days.put(date, PyramidScheme.parseStoredDay((String) stored));
            }
            date = date.plusDays(1);
        }
        return days;
    }
}

package com.k2040.escaagnellis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class PyramidReportFormat {
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT);
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MM/yy", Locale.ROOT);

    private PyramidReportFormat() {
    }

    static String day(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Missing date");
        return date.format(DAY);
    }

    static String range(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Missing date range");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Range start is after end");
        }
        return day(startDate) + " - " + day(endDate);
    }

    static String month(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Missing date");
        return date.format(MONTH);
    }

    static String period(
            PyramidReportModel.Section section,
            PyramidReportModel.PeriodRow row) {
        if (section == null || row == null) {
            throw new IllegalArgumentException("Missing report period");
        }
        switch (section) {
            case DETAILED_DAYS:
                return day(row.startDate);
            case MONTHLY_SUMMARIES:
                return month(row.startDate);
            case WEEKLY_SUMMARIES:
            case TOTAL_SUMMARY:
                return range(row.startDate, row.endDate);
            default:
                throw new IllegalArgumentException("Unsupported report section");
        }
    }
}

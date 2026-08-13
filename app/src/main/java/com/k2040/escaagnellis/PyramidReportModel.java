package com.k2040.escaagnellis;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PyramidReportModel {
    enum Section {
        DETAILED_DAYS,
        WEEKLY_SUMMARIES,
        MONTHLY_SUMMARIES,
        TOTAL_SUMMARY
    }

    private PyramidReportModel() {
    }

    static Report build(
            Map<LocalDate, PyramidScheme.DayState> recordedDays,
            LocalDate startDate,
            LocalDate endDate,
            Set<Section> requestedSections) {
        if (recordedDays == null) {
            throw new IllegalArgumentException("Missing recorded days");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Missing report date range");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Report start date is after end date");
        }
        if (requestedSections == null || requestedSections.isEmpty()) {
            throw new IllegalArgumentException("At least one report section is required");
        }

        EnumSet<Section> sections = EnumSet.copyOf(requestedSections);
        Map<LocalDate, Counts> dailyCounts = buildDailyCounts(
                recordedDays,
                startDate,
                endDate);

        List<PeriodRow> detailedDays = sections.contains(Section.DETAILED_DAYS)
                ? buildDetailedRows(dailyCounts)
                : Collections.emptyList();
        List<PeriodRow> weeklySummaries = sections.contains(Section.WEEKLY_SUMMARIES)
                ? buildWeeklyRows(dailyCounts, startDate, endDate)
                : Collections.emptyList();
        List<PeriodRow> monthlySummaries = sections.contains(Section.MONTHLY_SUMMARIES)
                ? buildMonthlyRows(dailyCounts, startDate, endDate)
                : Collections.emptyList();
        PeriodRow totalSummary = sections.contains(Section.TOTAL_SUMMARY)
                ? new PeriodRow(startDate, endDate, sumRange(dailyCounts, startDate, endDate))
                : null;

        return new Report(
                startDate,
                endDate,
                sections,
                detailedDays,
                weeklySummaries,
                monthlySummaries,
                totalSummary);
    }

    private static Map<LocalDate, Counts> buildDailyCounts(
            Map<LocalDate, PyramidScheme.DayState> recordedDays,
            LocalDate startDate,
            LocalDate endDate) {
        Map<LocalDate, Counts> result = new LinkedHashMap<>();
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            PyramidScheme.DayState day = recordedDays.get(date);
            result.put(date, day == null ? Counts.ZERO : countDay(day));
            date = date.plusDays(1);
        }
        return result;
    }

    private static Counts countDay(PyramidScheme.DayState source) {
        PyramidScheme.DayState day = PyramidScheme.fromBackupArrays(
                source.ticks,
                source.subtypeExtras);
        long green = 0L;
        long yellow = 0L;
        long red = 0L;

        for (int position = 0; position < day.ticks.length; position++) {
            if (!day.ticks[position]) continue;
            int subtype = PyramidScheme.subtypeForPosition(position);
            switch (colorForSubtype(subtype)) {
                case GREEN:
                    green++;
                    break;
                case YELLOW:
                    yellow++;
                    break;
                case RED:
                    red++;
                    break;
                default:
                    throw new IllegalStateException("Unhandled report colour");
            }
        }

        for (int subtype = 0; subtype < day.subtypeExtras.length; subtype++) {
            int extraCount = day.subtypeExtras[subtype];
            switch (colorForSubtype(subtype)) {
                case GREEN:
                    green = Math.addExact(green, extraCount);
                    break;
                case YELLOW:
                    yellow = Math.addExact(yellow, extraCount);
                    break;
                case RED:
                    red = Math.addExact(red, extraCount);
                    break;
                default:
                    throw new IllegalStateException("Unhandled report colour");
            }
        }

        return new Counts(green, yellow, red);
    }

    private static List<PeriodRow> buildDetailedRows(Map<LocalDate, Counts> dailyCounts) {
        List<PeriodRow> rows = new ArrayList<>(dailyCounts.size());
        for (Map.Entry<LocalDate, Counts> entry : dailyCounts.entrySet()) {
            rows.add(new PeriodRow(entry.getKey(), entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    private static List<PeriodRow> buildWeeklyRows(
            Map<LocalDate, Counts> dailyCounts,
            LocalDate startDate,
            LocalDate endDate) {
        List<PeriodRow> rows = new ArrayList<>();
        LocalDate periodStart = startDate;
        while (!periodStart.isAfter(endDate)) {
            LocalDate naturalWeekEnd = periodStart.with(
                    TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            LocalDate periodEnd = naturalWeekEnd.isAfter(endDate)
                    ? endDate
                    : naturalWeekEnd;
            rows.add(new PeriodRow(
                    periodStart,
                    periodEnd,
                    sumRange(dailyCounts, periodStart, periodEnd)));
            periodStart = periodEnd.plusDays(1);
        }
        return rows;
    }

    private static List<PeriodRow> buildMonthlyRows(
            Map<LocalDate, Counts> dailyCounts,
            LocalDate startDate,
            LocalDate endDate) {
        List<PeriodRow> rows = new ArrayList<>();
        LocalDate periodStart = startDate;
        while (!periodStart.isAfter(endDate)) {
            LocalDate naturalMonthEnd = YearMonth.from(periodStart).atEndOfMonth();
            LocalDate periodEnd = naturalMonthEnd.isAfter(endDate)
                    ? endDate
                    : naturalMonthEnd;
            rows.add(new PeriodRow(
                    periodStart,
                    periodEnd,
                    sumRange(dailyCounts, periodStart, periodEnd)));
            periodStart = periodEnd.plusDays(1);
        }
        return rows;
    }

    private static Counts sumRange(
            Map<LocalDate, Counts> dailyCounts,
            LocalDate startDate,
            LocalDate endDate) {
        Counts total = Counts.ZERO;
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            Counts counts = dailyCounts.get(date);
            if (counts == null) {
                throw new IllegalArgumentException("Date is outside the prepared report range");
            }
            total = total.plus(counts);
            date = date.plusDays(1);
        }
        return total;
    }

    private static ReportColor colorForSubtype(int subtype) {
        switch (subtype) {
            case PyramidScheme.SUBTYPE_EXTRAS:
                return ReportColor.RED;
            case PyramidScheme.SUBTYPE_OILS_FATS:
            case PyramidScheme.SUBTYPE_NUTS_SEEDS:
            case PyramidScheme.SUBTYPE_MILK_DAIRY:
            case PyramidScheme.SUBTYPE_PROTEIN:
                return ReportColor.YELLOW;
            case PyramidScheme.SUBTYPE_GRAINS:
            case PyramidScheme.SUBTYPE_SIDES:
            case PyramidScheme.SUBTYPE_PRODUCE:
            case PyramidScheme.SUBTYPE_DRINKS:
                return ReportColor.GREEN;
            default:
                throw new IllegalArgumentException("Unsupported pyramid subtype: " + subtype);
        }
    }

    private enum ReportColor {
        GREEN,
        YELLOW,
        RED
    }

    static final class Counts {
        static final Counts ZERO = new Counts(0L, 0L, 0L);

        final long green;
        final long yellow;
        final long red;

        Counts(long green, long yellow, long red) {
            if (green < 0L || yellow < 0L || red < 0L) {
                throw new IllegalArgumentException("Report counts must not be negative");
            }
            this.green = green;
            this.yellow = yellow;
            this.red = red;
        }

        Counts plus(Counts other) {
            if (other == null) throw new IllegalArgumentException("Missing report counts");
            return new Counts(
                    Math.addExact(green, other.green),
                    Math.addExact(yellow, other.yellow),
                    Math.addExact(red, other.red));
        }
    }

    static final class PeriodRow {
        final LocalDate startDate;
        final LocalDate endDate;
        final Counts counts;

        PeriodRow(LocalDate startDate, LocalDate endDate, Counts counts) {
            if (startDate == null || endDate == null || counts == null) {
                throw new IllegalArgumentException("Incomplete report row");
            }
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Report row start is after end");
            }
            this.startDate = startDate;
            this.endDate = endDate;
            this.counts = counts;
        }
    }

    static final class Report {
        final LocalDate startDate;
        final LocalDate endDate;
        final Set<Section> sections;
        final List<PeriodRow> detailedDays;
        final List<PeriodRow> weeklySummaries;
        final List<PeriodRow> monthlySummaries;
        final PeriodRow totalSummary;

        Report(
                LocalDate startDate,
                LocalDate endDate,
                Set<Section> sections,
                List<PeriodRow> detailedDays,
                List<PeriodRow> weeklySummaries,
                List<PeriodRow> monthlySummaries,
                PeriodRow totalSummary) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.sections = Collections.unmodifiableSet(EnumSet.copyOf(sections));
            this.detailedDays = Collections.unmodifiableList(new ArrayList<>(detailedDays));
            this.weeklySummaries = Collections.unmodifiableList(new ArrayList<>(weeklySummaries));
            this.monthlySummaries = Collections.unmodifiableList(new ArrayList<>(monthlySummaries));
            this.totalSummary = totalSummary;
        }
    }
}

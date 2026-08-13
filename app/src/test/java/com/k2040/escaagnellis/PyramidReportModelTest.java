package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class PyramidReportModelTest {
    @Test
    public void build_rejectsInvalidRangeAndEmptySections() {
        Map<LocalDate, PyramidScheme.DayState> days = Collections.emptyMap();
        LocalDate start = LocalDate.of(2026, 6, 2);
        LocalDate end = LocalDate.of(2026, 6, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidReportModel.build(
                        days,
                        start,
                        end,
                        EnumSet.of(PyramidReportModel.Section.TOTAL_SUMMARY)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidReportModel.build(
                        days,
                        end,
                        start,
                        Collections.emptySet()));
    }

    @Test
    public void dailyCounts_includeDefaultsAndExtrasInTheirSubtypeColour() {
        boolean[] ticks = new boolean[PyramidScheme.TILE_COUNT];
        int[] selectedPositions = new int[] { 0, 1, 3, 4, 6, 7, 10, 11, 16 };
        for (int position : selectedPositions) ticks[position] = true;
        int[] extras = new int[] { 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        LocalDate date = LocalDate.of(2026, 6, 18);
        Map<LocalDate, PyramidScheme.DayState> days = new LinkedHashMap<>();
        days.put(date, PyramidScheme.fromBackupArrays(ticks, extras));

        PyramidReportModel.Report report = PyramidReportModel.build(
                days,
                date,
                date,
                EnumSet.of(PyramidReportModel.Section.DETAILED_DAYS));

        PyramidReportModel.Counts counts = report.detailedDays.get(0).counts;
        assertEquals(38L, counts.green);
        assertEquals(22L, counts.yellow);
        assertEquals(3L, counts.red);
    }

    @Test
    public void detailedRows_includeEverySelectedDateAndExplicitZeros() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate recorded = LocalDate.of(2026, 6, 2);
        LocalDate end = LocalDate.of(2026, 6, 3);
        Map<LocalDate, PyramidScheme.DayState> days = new LinkedHashMap<>();
        days.put(recorded, day(new int[] { 16 }, null));

        PyramidReportModel.Report report = PyramidReportModel.build(
                days,
                start,
                end,
                EnumSet.of(PyramidReportModel.Section.DETAILED_DAYS));

        assertEquals(3, report.detailedDays.size());
        assertCounts(report.detailedDays.get(0).counts, 0L, 0L, 0L);
        assertCounts(report.detailedDays.get(1).counts, 1L, 0L, 0L);
        assertCounts(report.detailedDays.get(2).counts, 0L, 0L, 0L);
        assertEquals(start, report.detailedDays.get(0).startDate);
        assertEquals(end, report.detailedDays.get(2).endDate);
    }

    @Test
    public void weeklySummaries_useMondaySundayAndClipBoundaryWeeks() {
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 16);
        Map<LocalDate, PyramidScheme.DayState> days = new LinkedHashMap<>();
        days.put(LocalDate.of(2026, 6, 10), day(new int[] { 16 }, null));
        days.put(LocalDate.of(2026, 6, 14), day(new int[] { 0 }, null));
        days.put(LocalDate.of(2026, 6, 15), day(new int[] { 1 }, null));
        days.put(LocalDate.of(2026, 6, 16), day(new int[] { 16, 17 }, null));

        PyramidReportModel.Report report = PyramidReportModel.build(
                days,
                start,
                end,
                EnumSet.of(PyramidReportModel.Section.WEEKLY_SUMMARIES));

        assertEquals(2, report.weeklySummaries.size());
        PyramidReportModel.PeriodRow first = report.weeklySummaries.get(0);
        assertEquals(LocalDate.of(2026, 6, 10), first.startDate);
        assertEquals(LocalDate.of(2026, 6, 14), first.endDate);
        assertCounts(first.counts, 1L, 0L, 1L);

        PyramidReportModel.PeriodRow second = report.weeklySummaries.get(1);
        assertEquals(LocalDate.of(2026, 6, 15), second.startDate);
        assertEquals(LocalDate.of(2026, 6, 16), second.endDate);
        assertCounts(second.counts, 2L, 1L, 0L);
    }

    @Test
    public void monthlySummaries_clipSelectedBoundaryMonths() {
        LocalDate start = LocalDate.of(2026, 5, 30);
        LocalDate end = LocalDate.of(2026, 6, 2);
        Map<LocalDate, PyramidScheme.DayState> days = new LinkedHashMap<>();
        days.put(LocalDate.of(2026, 5, 31), day(new int[] { 0 }, null));
        days.put(LocalDate.of(2026, 6, 1), day(new int[] { 1 }, null));
        days.put(LocalDate.of(2026, 6, 2), day(new int[] { 16 }, null));

        PyramidReportModel.Report report = PyramidReportModel.build(
                days,
                start,
                end,
                EnumSet.of(PyramidReportModel.Section.MONTHLY_SUMMARIES));

        assertEquals(2, report.monthlySummaries.size());
        PyramidReportModel.PeriodRow may = report.monthlySummaries.get(0);
        assertEquals(LocalDate.of(2026, 5, 30), may.startDate);
        assertEquals(LocalDate.of(2026, 5, 31), may.endDate);
        assertCounts(may.counts, 0L, 0L, 1L);

        PyramidReportModel.PeriodRow june = report.monthlySummaries.get(1);
        assertEquals(LocalDate.of(2026, 6, 1), june.startDate);
        assertEquals(LocalDate.of(2026, 6, 2), june.endDate);
        assertCounts(june.counts, 1L, 1L, 0L);
    }

    @Test
    public void totalSummary_usesExactSelectedRangeAndMatchesDailySum() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 3);
        int[] extras = new int[PyramidScheme.SUBTYPE_COUNT];
        extras[PyramidScheme.SUBTYPE_PRODUCE] = 2;
        extras[PyramidScheme.SUBTYPE_OILS_FATS] = 3;
        extras[PyramidScheme.SUBTYPE_EXTRAS] = 4;
        Map<LocalDate, PyramidScheme.DayState> days = new LinkedHashMap<>();
        days.put(start, day(new int[] { 16, 1, 0 }, extras));
        days.put(end, day(new int[] { 17, 2 }, null));

        PyramidReportModel.Report report = PyramidReportModel.build(
                days,
                start,
                end,
                EnumSet.of(
                        PyramidReportModel.Section.DETAILED_DAYS,
                        PyramidReportModel.Section.TOTAL_SUMMARY));

        assertNotNull(report.totalSummary);
        assertEquals(start, report.totalSummary.startDate);
        assertEquals(end, report.totalSummary.endDate);
        assertCounts(report.totalSummary.counts, 4L, 5L, 5L);

        long green = 0L;
        long yellow = 0L;
        long red = 0L;
        for (PyramidReportModel.PeriodRow row : report.detailedDays) {
            green += row.counts.green;
            yellow += row.counts.yellow;
            red += row.counts.red;
        }
        assertCounts(report.totalSummary.counts, green, yellow, red);
    }

    @Test
    public void leapDay_isIncludedAsAnExplicitDailyRow() {
        LocalDate start = LocalDate.of(2028, 2, 28);
        LocalDate end = LocalDate.of(2028, 3, 1);

        PyramidReportModel.Report report = PyramidReportModel.build(
                Collections.emptyMap(),
                start,
                end,
                EnumSet.of(PyramidReportModel.Section.DETAILED_DAYS));

        assertEquals(3, report.detailedDays.size());
        assertEquals(LocalDate.of(2028, 2, 29), report.detailedDays.get(1).startDate);
        assertCounts(report.detailedDays.get(1).counts, 0L, 0L, 0L);
    }

    @Test
    public void onlyRequestedSections_arePopulated() {
        LocalDate date = LocalDate.of(2026, 6, 18);

        PyramidReportModel.Report report = PyramidReportModel.build(
                Collections.emptyMap(),
                date,
                date,
                EnumSet.of(PyramidReportModel.Section.TOTAL_SUMMARY));

        assertTrue(report.detailedDays.isEmpty());
        assertTrue(report.weeklySummaries.isEmpty());
        assertTrue(report.monthlySummaries.isEmpty());
        assertNotNull(report.totalSummary);

        PyramidReportModel.Report withoutTotal = PyramidReportModel.build(
                Collections.emptyMap(),
                date,
                date,
                EnumSet.of(PyramidReportModel.Section.DETAILED_DAYS));
        assertNull(withoutTotal.totalSummary);
    }

    private static PyramidScheme.DayState day(int[] selectedPositions, int[] extras) {
        boolean[] ticks = new boolean[PyramidScheme.TILE_COUNT];
        if (selectedPositions != null) {
            for (int position : selectedPositions) ticks[position] = true;
        }
        int[] safeExtras = extras == null
                ? new int[PyramidScheme.SUBTYPE_COUNT]
                : extras;
        return PyramidScheme.fromBackupArrays(ticks, safeExtras);
    }

    private static void assertCounts(
            PyramidReportModel.Counts counts,
            long green,
            long yellow,
            long red) {
        assertEquals(green, counts.green);
        assertEquals(yellow, counts.yellow);
        assertEquals(red, counts.red);
    }
}

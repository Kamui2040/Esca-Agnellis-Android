package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.LocalDate;

public class PyramidReportFormatTest {
    @Test
    public void formatsFixedDayRangeAndMonthValues() {
        LocalDate start = LocalDate.of(2026, 1, 2);
        LocalDate end = LocalDate.of(2026, 11, 12);

        assertEquals("02/01/26", PyramidReportFormat.day(start));
        assertEquals("02/01/26 - 12/11/26", PyramidReportFormat.range(start, end));
        assertEquals("01/26", PyramidReportFormat.month(start));
    }

    @Test
    public void formatsRowsBySectionType() {
        PyramidReportModel.PeriodRow row = new PyramidReportModel.PeriodRow(
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 3, 3),
                PyramidReportModel.Counts.ZERO);

        assertEquals(
                "29/02/28",
                PyramidReportFormat.period(
                        PyramidReportModel.Section.DETAILED_DAYS,
                        row));
        assertEquals(
                "02/28",
                PyramidReportFormat.period(
                        PyramidReportModel.Section.MONTHLY_SUMMARIES,
                        row));
        assertEquals(
                "29/02/28 - 03/03/28",
                PyramidReportFormat.period(
                        PyramidReportModel.Section.WEEKLY_SUMMARIES,
                        row));
    }

    @Test
    public void rejectsReversedRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PyramidReportFormat.range(
                        LocalDate.of(2026, 6, 2),
                        LocalDate.of(2026, 6, 1)));
    }
}

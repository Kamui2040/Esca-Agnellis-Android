package com.k2040.escaagnellis;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class PyramidPdfWriter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 36f;
    private static final float CONTENT_RIGHT = PAGE_WIDTH - MARGIN;
    private static final float CONTENT_BOTTOM = PAGE_HEIGHT - 54f;
    private static final float PERIOD_WIDTH = 250f;
    private static final float TABLE_HEADER_HEIGHT = 28f;
    private static final float ROW_HEIGHT = 24f;

    private static final int GREEN = Color.rgb(102, 153, 94);
    private static final int YELLOW = Color.rgb(221, 178, 63);
    private static final int RED = Color.rgb(194, 92, 82);

    private PyramidPdfWriter() {
    }

    static void write(
            OutputStream output,
            PyramidReportModel.Report report,
            Labels labels) throws IOException {
        if (output == null || report == null || labels == null) {
            throw new IllegalArgumentException("Incomplete PDF output");
        }

        PdfDocument document = new PdfDocument();
        PageCursor cursor = null;
        try {
            cursor = startPage(document, 1, report, labels);
            cursor = drawSection(
                    document,
                    cursor,
                    report,
                    labels,
                    PyramidReportModel.Section.DETAILED_DAYS,
                    labels.detailedSection,
                    report.detailedDays);
            cursor = drawSection(
                    document,
                    cursor,
                    report,
                    labels,
                    PyramidReportModel.Section.WEEKLY_SUMMARIES,
                    labels.weeklySection,
                    report.weeklySummaries);
            cursor = drawSection(
                    document,
                    cursor,
                    report,
                    labels,
                    PyramidReportModel.Section.MONTHLY_SUMMARIES,
                    labels.monthlySection,
                    report.monthlySummaries);
            if (report.totalSummary != null) {
                cursor = drawSection(
                        document,
                        cursor,
                        report,
                        labels,
                        PyramidReportModel.Section.TOTAL_SUMMARY,
                        labels.totalSection,
                        Collections.singletonList(report.totalSummary));
            }

            finishPage(document, cursor, labels);
            cursor = null;
            document.writeTo(output);
        } finally {
            if (cursor != null) {
                try {
                    finishPage(document, cursor, labels);
                } catch (Exception ignored) {
                    // Preserve the original write failure.
                }
            }
            document.close();
        }
    }

    private static PageCursor drawSection(
            PdfDocument document,
            PageCursor cursor,
            PyramidReportModel.Report report,
            Labels labels,
            PyramidReportModel.Section section,
            String sectionTitle,
            List<PyramidReportModel.PeriodRow> rows) {
        if (rows == null || rows.isEmpty()) return cursor;

        cursor = ensureSpace(
                document,
                cursor,
                report,
                labels,
                22f + TABLE_HEADER_HEIGHT + ROW_HEIGHT);
        drawSectionTitle(cursor.canvas, cursor.y, sectionTitle);
        cursor.y += 22f;
        drawTableHeader(cursor.canvas, cursor.y, labels);
        cursor.y += TABLE_HEADER_HEIGHT;

        for (PyramidReportModel.PeriodRow row : rows) {
            if (cursor.y + ROW_HEIGHT > CONTENT_BOTTOM) {
                finishPage(document, cursor, labels);
                cursor = startPage(document, cursor.pageNumber + 1, report, labels);
                drawSectionTitle(cursor.canvas, cursor.y, sectionTitle);
                cursor.y += 22f;
                drawTableHeader(cursor.canvas, cursor.y, labels);
                cursor.y += TABLE_HEADER_HEIGHT;
            }
            drawRow(cursor.canvas, cursor.y, section, row);
            cursor.y += ROW_HEIGHT;
        }
        cursor.y += 12f;
        return cursor;
    }

    private static PageCursor ensureSpace(
            PdfDocument document,
            PageCursor cursor,
            PyramidReportModel.Report report,
            Labels labels,
            float requiredHeight) {
        if (cursor.y + requiredHeight <= CONTENT_BOTTOM) return cursor;
        finishPage(document, cursor, labels);
        return startPage(document, cursor.pageNumber + 1, report, labels);
    }

    private static PageCursor startPage(
            PdfDocument document,
            int pageNumber,
            PyramidReportModel.Report report,
            Labels labels) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH,
                PAGE_HEIGHT,
                pageNumber).create();
        PdfDocument.Page page = document.startPage(info);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float y = 48f;
        paint.setColor(Color.rgb(45, 78, 49));
        paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        paint.setTextSize(18f);
        canvas.drawText(labels.title, MARGIN, y, paint);
        y += 22f;

        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        paint.setTextSize(10f);
        canvas.drawText(
                PyramidReportFormat.range(report.startDate, report.endDate),
                MARGIN,
                y,
                paint);
        y += 16f;

        if (pageNumber == 1) {
            paint.setTextSize(9f);
            y = drawWrappedText(canvas, labels.appVersion, MARGIN, y, contentWidth(), paint, 12f);
            y = drawWrappedText(
                    canvas,
                    labels.generatedOn,
                    MARGIN,
                    y,
                    contentWidth(),
                    paint,
                    12f);
            y = drawWrappedText(
                    canvas,
                    labels.pyramidDefinition,
                    MARGIN,
                    y + 2f,
                    contentWidth(),
                    paint,
                    12f);
            paint.setTypeface(Typeface.create("sans", Typeface.ITALIC));
            y = drawWrappedText(
                    canvas,
                    labels.notice,
                    MARGIN,
                    y + 2f,
                    contentWidth(),
                    paint,
                    12f);
            paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        }

        y += 8f;
        paint.setColor(Color.LTGRAY);
        paint.setStrokeWidth(1f);
        canvas.drawLine(MARGIN, y, CONTENT_RIGHT, y, paint);
        y += 18f;
        return new PageCursor(page, canvas, pageNumber, y);
    }

    private static void finishPage(
            PdfDocument document,
            PageCursor cursor,
            Labels labels) {
        if (cursor == null) return;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.LTGRAY);
        paint.setStrokeWidth(1f);
        canvasLine(cursor.canvas, MARGIN, PAGE_HEIGHT - 42f, CONTENT_RIGHT, PAGE_HEIGHT - 42f, paint);

        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        paint.setTextSize(8.5f);
        paint.setTextAlign(Paint.Align.RIGHT);
        cursor.canvas.drawText(
                String.format(Locale.ROOT, labels.pageFormat, cursor.pageNumber),
                CONTENT_RIGHT,
                PAGE_HEIGHT - 27f,
                paint);
        document.finishPage(cursor.page);
    }

    private static void drawSectionTitle(Canvas canvas, float y, String title) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(45, 78, 49));
        paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        paint.setTextSize(fittedTextSize(paint, title, contentWidth(), 12f, 9f));
        canvas.drawText(title, MARGIN, y + 14f, paint);
    }

    private static void drawTableHeader(Canvas canvas, float y, Labels labels) {
        float width = contentWidth();
        float countWidth = (width - PERIOD_WIDTH) / 3f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(239, 241, 238));
        canvas.drawRect(MARGIN, y, CONTENT_RIGHT, y + TABLE_HEADER_HEIGHT, paint);

        drawGrid(canvas, y, TABLE_HEADER_HEIGHT, countWidth);

        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(fittedTextSize(
                paint,
                labels.period,
                PERIOD_WIDTH - 16f,
                9f,
                7f));
        canvas.drawText(labels.period, MARGIN + 8f, y + 18f, paint);

        drawColorHeader(canvas, MARGIN + PERIOD_WIDTH, y, countWidth, labels.green, GREEN);
        drawColorHeader(
                canvas,
                MARGIN + PERIOD_WIDTH + countWidth,
                y,
                countWidth,
                labels.yellow,
                YELLOW);
        drawColorHeader(
                canvas,
                MARGIN + PERIOD_WIDTH + countWidth * 2f,
                y,
                countWidth,
                labels.red,
                RED);
    }

    private static void drawColorHeader(
            Canvas canvas,
            float left,
            float y,
            float width,
            String label,
            int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float square = 8f;
        float textLeft = left + 7f + square + 5f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(left + 7f, y + 10f, left + 7f + square, y + 10f + square, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.8f);
        paint.setColor(Color.DKGRAY);
        canvas.drawRect(left + 7f, y + 10f, left + 7f + square, y + 10f + square, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.DKGRAY);
        paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(fittedTextSize(
                paint,
                label,
                width - (textLeft - left) - 5f,
                8.5f,
                6.5f));
        canvas.drawText(label, textLeft, y + 18f, paint);
    }

    private static void drawRow(
            Canvas canvas,
            float y,
            PyramidReportModel.Section section,
            PyramidReportModel.PeriodRow row) {
        float width = contentWidth();
        float countWidth = (width - PERIOD_WIDTH) / 3f;
        drawGrid(canvas, y, ROW_HEIGHT, countWidth);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(40, 40, 40));
        paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.LEFT);
        String period = PyramidReportFormat.period(section, row);
        paint.setTextSize(fittedTextSize(paint, period, PERIOD_WIDTH - 16f, 9f, 7f));
        canvas.drawText(period, MARGIN + 8f, y + 16f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10f);
        canvas.drawText(
                Long.toString(row.counts.green),
                MARGIN + PERIOD_WIDTH + countWidth * .5f,
                y + 16f,
                paint);
        canvas.drawText(
                Long.toString(row.counts.yellow),
                MARGIN + PERIOD_WIDTH + countWidth * 1.5f,
                y + 16f,
                paint);
        canvas.drawText(
                Long.toString(row.counts.red),
                MARGIN + PERIOD_WIDTH + countWidth * 2.5f,
                y + 16f,
                paint);
    }

    private static void drawGrid(
            Canvas canvas,
            float y,
            float height,
            float countWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.7f);
        paint.setColor(Color.rgb(175, 175, 175));
        canvas.drawRect(MARGIN, y, CONTENT_RIGHT, y + height, paint);
        float x = MARGIN + PERIOD_WIDTH;
        for (int i = 0; i < 3; i++) {
            canvas.drawLine(x + countWidth * i, y, x + countWidth * i, y + height, paint);
        }
    }

    private static float drawWrappedText(
            Canvas canvas,
            String value,
            float x,
            float y,
            float maxWidth,
            Paint paint,
            float lineHeight) {
        if (value == null || value.trim().isEmpty()) return y;
        String[] words = value.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        float currentY = y;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), x, currentY, paint);
                currentY += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, currentY, paint);
            currentY += lineHeight;
        }
        return currentY;
    }

    private static float fittedTextSize(
            Paint paint,
            String value,
            float maxWidth,
            float preferred,
            float minimum) {
        float size = preferred;
        paint.setTextSize(size);
        while (size > minimum && paint.measureText(value) > maxWidth) {
            size -= .5f;
            paint.setTextSize(size);
        }
        return size;
    }

    private static void canvasLine(
            Canvas canvas,
            float startX,
            float startY,
            float stopX,
            float stopY,
            Paint paint) {
        canvas.drawLine(startX, startY, stopX, stopY, paint);
    }

    private static float contentWidth() {
        return CONTENT_RIGHT - MARGIN;
    }

    static final class Labels {
        final String title;
        final String period;
        final String green;
        final String yellow;
        final String red;
        final String detailedSection;
        final String weeklySection;
        final String monthlySection;
        final String totalSection;
        final String appVersion;
        final String generatedOn;
        final String pyramidDefinition;
        final String notice;
        final String pageFormat;

        Labels(
                String title,
                String period,
                String green,
                String yellow,
                String red,
                String detailedSection,
                String weeklySection,
                String monthlySection,
                String totalSection,
                String appVersion,
                String generatedOn,
                String pyramidDefinition,
                String notice,
                String pageFormat) {
            this.title = required(title);
            this.period = required(period);
            this.green = required(green);
            this.yellow = required(yellow);
            this.red = required(red);
            this.detailedSection = required(detailedSection);
            this.weeklySection = required(weeklySection);
            this.monthlySection = required(monthlySection);
            this.totalSection = required(totalSection);
            this.appVersion = required(appVersion);
            this.generatedOn = required(generatedOn);
            this.pyramidDefinition = required(pyramidDefinition);
            this.notice = required(notice);
            this.pageFormat = required(pageFormat);
        }

        private static String required(String value) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing PDF label");
            }
            return value;
        }
    }

    private static final class PageCursor {
        final PdfDocument.Page page;
        final Canvas canvas;
        final int pageNumber;
        float y;

        PageCursor(PdfDocument.Page page, Canvas canvas, int pageNumber, float y) {
            this.page = page;
            this.canvas = canvas;
            this.pageNumber = pageNumber;
            this.y = y;
        }
    }
}

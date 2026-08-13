package com.k2040.escaagnellis;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

final class PyramidPdfExportDialog {
    interface Callback {
        void onExport(Config config);
    }

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT);

    private PyramidPdfExportDialog() {
    }

    static void show(
            Activity activity,
            int dialogTheme,
            Palette palette,
            LocalDate initialStart,
            LocalDate initialEnd,
            Callback callback) {
        if (activity == null
                || palette == null
                || initialStart == null
                || initialEnd == null
                || callback == null) {
            throw new IllegalArgumentException("Incomplete PDF export dialog configuration");
        }

        int padding = dp(activity, 20);
        int gap = dp(activity, 10);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, dp(activity, 16), padding, dp(activity, 4));
        content.setBackgroundColor(palette.surface);

        TextView title = new TextView(activity);
        title.setText(R.string.pdf_export_title);
        title.setTextColor(palette.primaryText);
        title.setTextSize(24f);
        content.addView(title, matchWrap());

        TextView subtitle = new TextView(activity);
        subtitle.setText(R.string.pdf_export_subtitle);
        subtitle.setTextColor(palette.secondaryText);
        subtitle.setTextSize(14f);
        content.addView(subtitle, topMargin(matchWrap(), dp(activity, 6)));

        final LocalDate[] dates = new LocalDate[] { initialStart, initialEnd };

        TextView startLabel = label(activity, R.string.pdf_start_date, palette);
        content.addView(startLabel, topMargin(matchWrap(), dp(activity, 18)));

        Button startButton = dateButton(activity, dates[0], palette);
        content.addView(startButton, topMargin(matchWrap(), dp(activity, 4)));
        startButton.setOnClickListener(view -> showDatePicker(
                activity,
                dialogTheme,
                palette,
                dates[0],
                selected -> {
                    dates[0] = selected;
                    startButton.setText(displayDate(selected));
                }));

        TextView endLabel = label(activity, R.string.pdf_end_date, palette);
        content.addView(endLabel, topMargin(matchWrap(), gap));

        Button endButton = dateButton(activity, dates[1], palette);
        content.addView(endButton, topMargin(matchWrap(), dp(activity, 4)));
        endButton.setOnClickListener(view -> showDatePicker(
                activity,
                dialogTheme,
                palette,
                dates[1],
                selected -> {
                    dates[1] = selected;
                    endButton.setText(displayDate(selected));
                }));

        CheckBox detailed = checkbox(activity, R.string.pdf_section_detailed, palette);
        CheckBox weekly = checkbox(activity, R.string.pdf_section_weekly, palette);
        CheckBox monthly = checkbox(activity, R.string.pdf_section_monthly, palette);
        CheckBox total = checkbox(activity, R.string.pdf_section_total, palette);
        detailed.setChecked(true);
        weekly.setChecked(true);
        monthly.setChecked(true);
        total.setChecked(true);

        content.addView(detailed, topMargin(matchWrap(), gap));
        content.addView(weekly, matchWrap());
        content.addView(monthly, matchWrap());
        content.addView(total, matchWrap());

        TextView error = new TextView(activity);
        error.setTextColor(palette.errorText);
        error.setTextSize(12f);
        error.setVisibility(View.GONE);
        content.addView(error, topMargin(matchWrap(), gap));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.surface);
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(activity, dialogTheme)
                .setView(scroll)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.pdf_action_export, null)
                .create();

        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setWindowAnimations(0);
        }

        dialog.setOnShowListener(ignored -> {
            applyDialogChrome(activity, dialog, palette);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        if (dates[0].isAfter(dates[1])) {
                            showError(error, activity.getString(R.string.pdf_error_date_order));
                            return;
                        }

                        EnumSet<PyramidReportModel.Section> sections =
                                EnumSet.noneOf(PyramidReportModel.Section.class);
                        if (detailed.isChecked()) {
                            sections.add(PyramidReportModel.Section.DETAILED_DAYS);
                        }
                        if (weekly.isChecked()) {
                            sections.add(PyramidReportModel.Section.WEEKLY_SUMMARIES);
                        }
                        if (monthly.isChecked()) {
                            sections.add(PyramidReportModel.Section.MONTHLY_SUMMARIES);
                        }
                        if (total.isChecked()) {
                            sections.add(PyramidReportModel.Section.TOTAL_SUMMARY);
                        }
                        if (sections.isEmpty()) {
                            showError(
                                    error,
                                    activity.getString(R.string.pdf_error_section_required));
                            return;
                        }

                        callback.onExport(new Config(dates[0], dates[1], sections));
                        dialog.dismiss();
                    });
        });
        dialog.show();
    }

    private interface DateCallback {
        void onDate(LocalDate date);
    }

    private static void showDatePicker(
            Activity activity,
            int dialogTheme,
            Palette palette,
            LocalDate initial,
            DateCallback callback) {
        DatePickerDialog picker = new DatePickerDialog(
                activity,
                dialogTheme,
                (view, year, month, dayOfMonth) ->
                        callback.onDate(LocalDate.of(year, month + 1, dayOfMonth)),
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth());

        picker.setOnShowListener(ignored -> {
            applyDialogChrome(activity, picker, palette);
            DatePicker datePicker = picker.getDatePicker();
            datePicker.setBackgroundColor(palette.surface);
            tintTextTree(datePicker, palette.primaryText);

            int headerId = activity.getResources().getIdentifier(
                    "date_picker_header",
                    "id",
                    "android");
            if (headerId != 0) {
                View header = picker.findViewById(headerId);
                if (header != null) {
                    header.setBackgroundColor(palette.accent);
                    tintTextTree(header, palette.textOnAccent);
                }
            }
        });
        picker.show();
    }

    private static void applyDialogChrome(
            Activity activity,
            AlertDialog dialog,
            Palette palette) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundedDrawable(
                    palette.surface,
                    palette.outline,
                    dp(activity, 18),
                    dp(activity, 1)));
        }

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(palette.actionText);
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(palette.actionText);
        }
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutral != null) {
            neutral.setTextColor(palette.actionText);
        }
    }

    private static TextView label(
            Activity activity,
            int stringId,
            Palette palette) {
        TextView label = new TextView(activity);
        label.setText(stringId);
        label.setTextColor(palette.secondaryText);
        label.setTextSize(13f);
        return label;
    }

    private static Button dateButton(
            Activity activity,
            LocalDate date,
            Palette palette) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(displayDate(date));
        button.setTextColor(palette.primaryText);
        button.setTextSize(16f);
        button.setMinHeight(dp(activity, 56));
        button.setBackground(roundedDrawable(
                palette.background,
                palette.outline,
                dp(activity, 8),
                dp(activity, 1)));
        return button;
    }

    private static CheckBox checkbox(
            Activity activity,
            int stringId,
            Palette palette) {
        CheckBox box = new CheckBox(activity);
        box.setText(stringId);
        box.setTextColor(palette.primaryText);
        box.setTextSize(14f);
        box.setButtonTintList(new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] { -android.R.attr.state_enabled },
                        new int[] {}
                },
                new int[] {
                        palette.accent,
                        blend(palette.outline, palette.surface, .35f),
                        palette.secondaryText
                }));
        return box;
    }

    private static void tintTextTree(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                tintTextTree(group.getChildAt(index), color);
            }
        }
    }

    private static GradientDrawable roundedDrawable(
            int fill,
            int stroke,
            int radius,
            int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams topMargin(
            LinearLayout.LayoutParams params,
            int topMargin) {
        params.topMargin = topMargin;
        return params;
    }

    private static void showError(TextView error, String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String displayDate(LocalDate date) {
        return date.format(DISPLAY_DATE);
    }

    private static int ensureTextContrast(
            int foreground,
            int background,
            boolean darkBackground) {
        int candidate = foreground;
        int target = darkBackground ? Color.WHITE : Color.BLACK;
        for (int step = 0; step < 12 && contrastRatio(candidate, background) < 4.5; step++) {
            candidate = blend(candidate, target, .14f);
        }
        if (contrastRatio(candidate, background) < 4.5) {
            candidate = bestTextColor(background);
        }
        return candidate;
    }

    private static int bestTextColor(int background) {
        return contrastRatio(Color.WHITE, background)
                >= contrastRatio(Color.BLACK, background)
                ? Color.WHITE
                : Color.BLACK;
    }

    private static int blend(int first, int second, float secondWeight) {
        float weight = Math.max(0f, Math.min(1f, secondWeight));
        float firstWeight = 1f - weight;
        return Color.rgb(
                Math.round(Color.red(first) * firstWeight + Color.red(second) * weight),
                Math.round(Color.green(first) * firstWeight + Color.green(second) * weight),
                Math.round(Color.blue(first) * firstWeight + Color.blue(second) * weight));
    }

    private static double contrastRatio(int first, int second) {
        double firstLum = relativeLuminance(first);
        double secondLum = relativeLuminance(second);
        double lighter = Math.max(firstLum, secondLum);
        double darker = Math.min(firstLum, secondLum);
        return (lighter + .05) / (darker + .05);
    }

    private static double relativeLuminance(int color) {
        double red = linearChannel(Color.red(color) / 255.0);
        double green = linearChannel(Color.green(color) / 255.0);
        double blue = linearChannel(Color.blue(color) / 255.0);
        return .2126 * red + .7152 * green + .0722 * blue;
    }

    private static double linearChannel(double channel) {
        return channel <= .04045
                ? channel / 12.92
                : Math.pow((channel + .055) / 1.055, 2.4);
    }

    static final class Palette {
        final int background;
        final int surface;
        final int primaryText;
        final int secondaryText;
        final int accent;
        final int outline;
        final int actionText;
        final int errorText;
        final int textOnAccent;

        Palette(
                int background,
                int surface,
                int primaryText,
                int secondaryText,
                int accent,
                int outline,
                int error,
                boolean dark) {
            this.background = background;
            this.surface = surface;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.accent = accent;
            this.outline = outline;
            this.actionText = ensureTextContrast(accent, surface, dark);
            this.errorText = ensureTextContrast(error, surface, dark);
            this.textOnAccent = bestTextColor(accent);
        }
    }

    static final class Config {
        final LocalDate startDate;
        final LocalDate endDate;
        final Set<PyramidReportModel.Section> sections;

        Config(
                LocalDate startDate,
                LocalDate endDate,
                Set<PyramidReportModel.Section> sections) {
            if (startDate == null || endDate == null || sections == null || sections.isEmpty()) {
                throw new IllegalArgumentException("Incomplete PDF export configuration");
            }
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("PDF export start date is after end date");
            }
            this.startDate = startDate;
            this.endDate = endDate;
            this.sections = EnumSet.copyOf(sections);
        }

        void writeToBundle(Bundle bundle, String prefix) {
            if (bundle == null || prefix == null) return;
            bundle.putString(prefix + "start", startDate.toString());
            bundle.putString(prefix + "end", endDate.toString());
            for (PyramidReportModel.Section section : PyramidReportModel.Section.values()) {
                bundle.putBoolean(prefix + section.name(), sections.contains(section));
            }
        }

        static Config readFromBundle(Bundle bundle, String prefix) {
            if (bundle == null || prefix == null || !bundle.containsKey(prefix + "start")) {
                return null;
            }
            try {
                LocalDate start = LocalDate.parse(bundle.getString(prefix + "start"));
                LocalDate end = LocalDate.parse(bundle.getString(prefix + "end"));
                EnumSet<PyramidReportModel.Section> sections =
                        EnumSet.noneOf(PyramidReportModel.Section.class);
                for (PyramidReportModel.Section section : PyramidReportModel.Section.values()) {
                    if (bundle.getBoolean(prefix + section.name(), false)) {
                        sections.add(section);
                    }
                }
                return sections.isEmpty() ? null : new Config(start, end, sections);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}

package com.k2040.escaagnellis;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;

final class AppLanguage {
    static final String PREFERENCES_NAME = "esca_ui_v1";
    static final String PREFERENCE_KEY = "language_tag";
    static final String SYSTEM = "system";

    private static final String[] TAGS = {
            SYSTEM,
            "de",
            "en",
            "es",
            "fr",
            "pt-PT"
    };

    private AppLanguage() {
    }

    static String normalizeStoredValue(String value) {
        if (value == null) return SYSTEM;
        String trimmed = value.trim();
        for (String tag : TAGS) {
            if (tag.equalsIgnoreCase(trimmed)) return tag;
        }
        return SYSTEM;
    }

    static String tagAt(int index) {
        if (index < 0 || index >= TAGS.length) return SYSTEM;
        return TAGS[index];
    }

    static int indexOf(String value) {
        String normalized = normalizeStoredValue(value);
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equals(normalized)) return i;
        }
        return 0;
    }

    static Locale explicitLocale(String value) {
        String normalized = normalizeStoredValue(value);
        return SYSTEM.equals(normalized) ? null : Locale.forLanguageTag(normalized);
    }

    static Locale resolveLocale(String value, Locale... systemLocales) {
        Locale explicit = explicitLocale(value);
        if (explicit != null) return explicit;
        if (systemLocales != null) {
            for (Locale locale : systemLocales) {
                if (isSupportedSystemLocale(locale)) return locale;
            }
        }
        return Locale.GERMAN;
    }

    static String compactWeekdayLabel(DayOfWeek dayOfWeek, Locale locale) {
        if (dayOfWeek == null || locale == null) {
            throw new IllegalArgumentException("Missing weekday locale");
        }
        String label = dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replace(".", "");
        int codePoints = label.codePointCount(0, label.length());
        int end = label.offsetByCodePoints(0, Math.min(3, codePoints));
        return label.substring(0, end);
    }

    private static boolean isSupportedSystemLocale(Locale locale) {
        if (locale == null) return false;
        String language = locale.getLanguage();
        if ("de".equals(language)
                || "en".equals(language)
                || "es".equals(language)
                || "fr".equals(language)) {
            return true;
        }
        return "pt".equals(language) && "PT".equalsIgnoreCase(locale.getCountry());
    }
}

package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.DayOfWeek;
import java.util.Locale;

public class AppLanguageTest {
    @Test
    public void normalizesSupportedStoredValues() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalizeStoredValue(null));
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalizeStoredValue("system"));
        assertEquals("de", AppLanguage.normalizeStoredValue("de"));
        assertEquals("en", AppLanguage.normalizeStoredValue("EN"));
        assertEquals("es", AppLanguage.normalizeStoredValue(" es "));
        assertEquals("fr", AppLanguage.normalizeStoredValue("fr"));
        assertEquals("pt-PT", AppLanguage.normalizeStoredValue("pt-pt"));
    }

    @Test
    public void unsupportedStoredValuesFallBackToSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalizeStoredValue(""));
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalizeStoredValue("pt-BR"));
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalizeStoredValue("it"));
    }

    @Test
    public void stableIndexMappingRoundTrips() {
        String[] expected = {"system", "de", "en", "es", "fr", "pt-PT"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], AppLanguage.tagAt(i));
            assertEquals(i, AppLanguage.indexOf(expected[i]));
        }
        assertEquals(AppLanguage.SYSTEM, AppLanguage.tagAt(-1));
        assertEquals(AppLanguage.SYSTEM, AppLanguage.tagAt(99));
    }

    @Test
    public void explicitLocaleMappingExcludesSystem() {
        assertNull(AppLanguage.explicitLocale(AppLanguage.SYSTEM));
        assertEquals("de", AppLanguage.explicitLocale("de").toLanguageTag());
        assertEquals("en", AppLanguage.explicitLocale("en").toLanguageTag());
        assertEquals("es", AppLanguage.explicitLocale("es").toLanguageTag());
        assertEquals("fr", AppLanguage.explicitLocale("fr").toLanguageTag());
        assertEquals("pt-PT", AppLanguage.explicitLocale("pt-PT").toLanguageTag());
    }

    @Test
    public void systemModeUsesTheFirstSupportedDeviceLocale() {
        assertEquals("en-US", AppLanguage.resolveLocale(
                AppLanguage.SYSTEM,
                Locale.ITALIAN,
                Locale.forLanguageTag("en-US")).toLanguageTag());
        assertEquals("pt-PT", AppLanguage.resolveLocale(
                AppLanguage.SYSTEM,
                Locale.forLanguageTag("pt-PT")).toLanguageTag());
    }

    @Test
    public void unsupportedSystemLocalesFallBackToGerman() {
        assertEquals("de", AppLanguage.resolveLocale(
                AppLanguage.SYSTEM,
                Locale.ITALIAN).toLanguageTag());
        assertEquals("de", AppLanguage.resolveLocale(
                AppLanguage.SYSTEM,
                Locale.forLanguageTag("pt-BR")).toLanguageTag());
        assertEquals("de", AppLanguage.resolveLocale(
                AppLanguage.SYSTEM).toLanguageTag());
    }

    @Test
    public void explicitLanguageIgnoresDeviceLocales() {
        assertEquals("fr", AppLanguage.resolveLocale(
                "fr",
                Locale.forLanguageTag("en-US")).toLanguageTag());
    }

    @Test
    public void compactWeekdayLabelsKeepExistingShortLocalesStable() {
        assertEquals("Mo", AppLanguage.compactWeekdayLabel(
                DayOfWeek.MONDAY, Locale.GERMAN));
        assertEquals("Mon", AppLanguage.compactWeekdayLabel(
                DayOfWeek.MONDAY, Locale.ENGLISH));
        assertEquals("mié", AppLanguage.compactWeekdayLabel(
                DayOfWeek.WEDNESDAY, Locale.forLanguageTag("es")));
        assertEquals("mer", AppLanguage.compactWeekdayLabel(
                DayOfWeek.WEDNESDAY, Locale.FRENCH));
    }

    @Test
    public void compactWeekdayLabelsBoundPortugueseToThreeCodePoints() {
        Locale portuguese = Locale.forLanguageTag("pt-PT");
        String[] expected = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};
        DayOfWeek[] days = DayOfWeek.values();

        assertEquals(expected.length, days.length);
        for (int i = 0; i < days.length; i++) {
            assertEquals(expected[i], AppLanguage.compactWeekdayLabel(
                    days[i], portuguese));
        }
    }
}

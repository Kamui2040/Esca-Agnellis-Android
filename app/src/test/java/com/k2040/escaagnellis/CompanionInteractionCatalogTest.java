package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.List;

public class CompanionInteractionCatalogTest {
    @Test
    public void catalogMatchesPlannedIdsCostsAndLabels() {
        List<CompanionInteractionCatalog.Entry> entries = CompanionInteractionCatalog.all();

        assertEquals(3, entries.size());
        assertEntry(entries.get(0), "feed_treat", 2L, R.string.companion_interaction_feed_treat);
        assertEntry(entries.get(1), "play", 4L, R.string.companion_interaction_play);
        assertEntry(entries.get(2), "cuddle", 6L, R.string.companion_interaction_cuddle);

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                assertNotEquals(entries.get(i).id, entries.get(j).id);
            }
        }
    }

    private static void assertEntry(
            CompanionInteractionCatalog.Entry entry,
            String id,
            long cost,
            int labelResId) {
        assertEquals(id, entry.id);
        assertEquals(cost, entry.cost);
        assertEquals(labelResId, entry.labelResId);
    }
}

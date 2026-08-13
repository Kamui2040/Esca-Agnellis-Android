package com.k2040.escaagnellis;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class CompanionInteractionCatalog {
    private static final List<Entry> ALL = Collections.unmodifiableList(Arrays.asList(
            new Entry("feed_treat", 2L, R.string.companion_interaction_feed_treat),
            new Entry("play", 4L, R.string.companion_interaction_play),
            new Entry("cuddle", 6L, R.string.companion_interaction_cuddle)));

    private CompanionInteractionCatalog() {
    }

    static List<Entry> all() {
        return ALL;
    }

    static final class Entry {
        final String id;
        final long cost;
        final int labelResId;

        Entry(String id, long cost, int labelResId) {
            this.id = CompanionState.requireStableId(id, "interaction id");
            if (cost <= 0L) {
                throw new IllegalArgumentException("Interaction cost must be positive");
            }
            this.cost = cost;
            this.labelResId = labelResId;
        }
    }
}

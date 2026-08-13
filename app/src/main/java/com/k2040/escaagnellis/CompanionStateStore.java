package com.k2040.escaagnellis;

import java.util.Map;

interface CompanionStateStore {
    Map<String, ?> readAll();

    WriteOutcome replaceAll(Map<String, ?> values);

    enum WriteOutcome {
        TARGET_APPLIED,
        ORIGINAL_RESTORED,
        INDETERMINATE
    }
}

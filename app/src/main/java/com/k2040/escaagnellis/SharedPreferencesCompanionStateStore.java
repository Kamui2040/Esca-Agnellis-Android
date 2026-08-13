package com.k2040.escaagnellis;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class SharedPreferencesCompanionStateStore implements CompanionStateStore {
    static final String PREFERENCES_NAME = "esca_companion_v1";

    private final SharedPreferences preferences;

    SharedPreferencesCompanionStateStore(Context applicationContext) {
        if (applicationContext == null) {
            throw new IllegalArgumentException("Missing application context");
        }
        preferences = applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE);
    }

    @Override
    public Map<String, ?> readAll() {
        return Collections.unmodifiableMap(copySnapshot(preferences.getAll()));
    }

    @Override
    public WriteOutcome replaceAll(Map<String, ?> values) {
        Map<String, Object> target = copySnapshot(values);
        Map<String, Object> original = copySnapshot(preferences.getAll());

        boolean targetCommitReported = writeSnapshot(target);
        Map<String, Object> observedAfterTarget = copySnapshot(preferences.getAll());
        if (targetCommitReported && target.equals(observedAfterTarget)) {
            return WriteOutcome.TARGET_APPLIED;
        }

        boolean rollbackCommitReported = writeSnapshot(original);
        Map<String, Object> observedAfterRollback = copySnapshot(preferences.getAll());
        if (rollbackCommitReported && original.equals(observedAfterRollback)) {
            return WriteOutcome.ORIGINAL_RESTORED;
        }
        return WriteOutcome.INDETERMINATE;
    }

    private boolean writeSnapshot(Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Set<?>) {
                editor.putStringSet(key, copyStringSet((Set<?>) value));
            } else {
                throw new IllegalArgumentException("Unsupported companion preference value");
            }
        }
        return editor.commit();
    }

    private static Map<String, Object> copySnapshot(Map<String, ?> source) {
        if (source == null) {
            throw new IllegalArgumentException("Missing companion preference snapshot");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                throw new IllegalArgumentException("Invalid companion preference entry");
            }
            if (value instanceof String
                    || value instanceof Boolean
                    || value instanceof Integer
                    || value instanceof Long
                    || value instanceof Float) {
                copy.put(key, value);
            } else if (value instanceof Set<?>) {
                copy.put(key, copyStringSet((Set<?>) value));
            } else {
                throw new IllegalArgumentException("Unsupported companion preference value");
            }
        }
        return copy;
    }

    private static Set<String> copyStringSet(Set<?> source) {
        Set<String> copy = new HashSet<>();
        for (Object value : source) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Invalid companion preference string set");
            }
            copy.add((String) value);
        }
        return Collections.unmodifiableSet(copy);
    }
}

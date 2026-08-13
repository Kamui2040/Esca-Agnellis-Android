package com.k2040.escaagnellis;

final class PyramidInteractionRules {
    private PyramidInteractionRules() {
    }

    static final class ExtraDisplay {
        final int visible;
        final int overflow;

        ExtraDisplay(int visible, int overflow) {
            this.visible = visible;
            this.overflow = overflow;
        }
    }

    static ExtraDisplay extraDisplay(int extras, int maxVisibleExtras) {
        int safeExtras = Math.max(0, extras);
        int safeMax = Math.max(0, maxVisibleExtras);
        int visible = Math.min(safeMax, safeExtras);
        return new ExtraDisplay(visible, Math.max(0, safeExtras - safeMax));
    }

    static boolean fillFirstUnticked(boolean[] ticks, int start, int count) {
        int end = safeEnd(ticks, start, count);
        for (int i = Math.max(0, start); i < end; i++) {
            if (!ticks[i]) {
                ticks[i] = true;
                return true;
            }
        }
        return false;
    }

    static boolean removeFromRight(boolean[] ticks, int start, int count, int[] extras, int extraIndex) {
        if (extras != null && extraIndex >= 0 && extraIndex < extras.length && extras[extraIndex] > 0) {
            extras[extraIndex]--;
            return true;
        }
        return removeDefaultFromRight(ticks, start, count);
    }

    static boolean removeDefaultFromRight(boolean[] ticks, int start, int count) {
        for (int i = safeEnd(ticks, start, count) - 1; i >= Math.max(0, start); i--) {
            if (ticks[i]) {
                ticks[i] = false;
                return true;
            }
        }
        return false;
    }

    static float gapForVisibleCount(int totalVisible, float density) {
        float dp = Math.max(0.1f, density);
        if (totalVisible >= 13) return 1.6f * dp;
        if (totalVisible >= 12) return 2.0f * dp;
        if (totalVisible >= 10) return 2.4f * dp;
        if (totalVisible >= 9) return 2.8f * dp;
        if (totalVisible >= 7) return 4.0f * dp;
        return 7.0f * dp;
    }

    static float tileForVisibleCount(float availableW, int totalVisible, float gap, float density) {
        if (totalVisible <= 0) return 0f;
        float safeAvailable = Math.max(0f, availableW);
        float safeGap = Math.max(0f, gap);
        float fitTile = (safeAvailable - safeGap * Math.max(0, totalVisible - 1)) / totalVisible;
        float maxTile = 54f * Math.max(0.1f, density);
        float readableMin = 16f * Math.max(0.1f, density);
        float tile = Math.min(maxTile, fitTile);
        if (tile >= readableMin) return tile;
        return Math.max(1f, tile);
    }

    static float rowWidth(int totalVisible, float tile, float gap) {
        if (totalVisible <= 0) return 0f;
        return totalVisible * tile + Math.max(0, totalVisible - 1) * gap;
    }

    private static int safeEnd(boolean[] ticks, int start, int count) {
        if (ticks == null || count <= 0) return 0;
        return Math.min(ticks.length, Math.max(0, start) + count);
    }
}

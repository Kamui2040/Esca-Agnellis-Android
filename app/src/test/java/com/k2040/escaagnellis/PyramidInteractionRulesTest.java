package com.k2040.escaagnellis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PyramidInteractionRulesTest {
    @Test
    public void fillFirstUnticked_usesLeftmostOpenDefault() {
        boolean[] ticks = new boolean[] { true, false, false, true };

        boolean changed = PyramidInteractionRules.fillFirstUnticked(ticks, 0, 4);

        assertTrue(changed);
        assertArrayEquals(new boolean[] { true, true, false, true }, ticks);
    }

    @Test
    public void removeFromRight_removesExtrasBeforeDefaults() {
        boolean[] ticks = new boolean[] { true, true, true };
        int[] extras = new int[] { 2 };

        PyramidInteractionRules.removeFromRight(ticks, 0, 3, extras, 0);
        PyramidInteractionRules.removeFromRight(ticks, 0, 3, extras, 0);
        PyramidInteractionRules.removeFromRight(ticks, 0, 3, extras, 0);

        assertEquals(0, extras[0]);
        assertArrayEquals(new boolean[] { true, true, false }, ticks);
    }

    @Test
    public void removeFromRight_changesOnlyRequestedExtraCounter() {
        boolean[] ticks = new boolean[] { true, true, true, true, true };
        int[] extras = new int[] { 2, 4 };

        PyramidInteractionRules.removeFromRight(ticks, 0, 5, extras, 0);

        assertEquals(1, extras[0]);
        assertEquals(4, extras[1]);
        assertArrayEquals(new boolean[] { true, true, true, true, true }, ticks);
    }

    @Test
    public void extraDisplay_allowsThreeVisibleExtrasForNormalRows() {
        PyramidInteractionRules.ExtraDisplay display =
                PyramidInteractionRules.extraDisplay(5, 3);

        assertEquals(3, display.visible);
        assertEquals(2, display.overflow);
    }

    @Test
    public void extraDisplay_zeroExtrasHasNoVisibleTileOrOverflow() {
        PyramidInteractionRules.ExtraDisplay display =
                PyramidInteractionRules.extraDisplay(0, 3);

        assertEquals(0, display.visible);
        assertEquals(0, display.overflow);
    }

    @Test
    public void tileSizing_keepsCrowdedCurrentRowInsideAvailableWidth() {
        int totalVisible = 10;
        float density = 1f;
        float availableWidth = 260f;
        float gap = PyramidInteractionRules.gapForVisibleCount(totalVisible, density);
        float tile = PyramidInteractionRules.tileForVisibleCount(
                availableWidth,
                totalVisible,
                gap,
                density);

        float rowWidth = PyramidInteractionRules.rowWidth(totalVisible, tile, gap);

        assertTrue(rowWidth <= availableWidth + 0.001f);
        assertTrue(tile > 0f);
    }

}

package com.k2040.escaagnellis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CompanionAffordabilityTest {
    @Test
    public void usesFixedCostBoundaries() {
        assertFalse(CompanionAffordability.isAffordable(0L, 2L));
        assertFalse(CompanionAffordability.isAffordable(1L, 2L));
        assertTrue(CompanionAffordability.isAffordable(2L, 2L));
        assertFalse(CompanionAffordability.isAffordable(3L, 4L));
        assertTrue(CompanionAffordability.isAffordable(4L, 4L));
        assertFalse(CompanionAffordability.isAffordable(5L, 6L));
        assertTrue(CompanionAffordability.isAffordable(6L, 6L));
        assertTrue(CompanionAffordability.isAffordable(Long.MAX_VALUE, 6L));
    }

    @Test
    public void appliesAllCatalogCosts() {
        for (CompanionInteractionCatalog.Entry entry : CompanionInteractionCatalog.all()) {
            assertTrue(CompanionAffordability.isAffordable(entry.cost, entry.cost));
            assertFalse(CompanionAffordability.isAffordable(entry.cost - 1L, entry.cost));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeBalance() {
        CompanionAffordability.isAffordable(-1L, 2L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveCost() {
        CompanionAffordability.isAffordable(2L, 0L);
    }
}

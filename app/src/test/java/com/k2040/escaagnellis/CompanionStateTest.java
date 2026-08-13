package com.k2040.escaagnellis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CompanionStateTest {
    @Test
    public void companionNameNormalizesWhitespaceAndClearsEmptyInput() {
        CompanionState named = CompanionState.disabledDefault()
                .withCompanionName("  Luna   Star  ");
        CompanionState cleared = named.withCompanionName("   ");

        assertEquals("Luna Star", named.companionName);
        assertNull(cleared.companionName);
        assertEquals(cleared, cleared.withCompanionName(null));
    }

    @Test
    public void companionNameRejectsControlCharactersAndLongValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionState.disabledDefault().withCompanionName("Luna\nStar"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompanionState.disabledDefault().withCompanionName("1234567890123456789012345"));
    }
}

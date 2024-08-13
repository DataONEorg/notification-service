package org.dataone.notifications.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void isBlankWithNullString() {
        assertTrue(StringUtils.isBlank(null));
    }

    @Test
    void isBlankWithEmptyString() {
        assertTrue(StringUtils.isBlank(""));
    }

    @Test
    void isBlankWithNewlineOnly() {
        assertTrue(StringUtils.isBlank("\n"));
    }

    @Test
    void isBlankWithSpacesString() {
        assertTrue(StringUtils.isBlank("    "));
    }

    @Test
    void isBlankWithWhitespaceString() {
        assertTrue(StringUtils.isBlank(" \t \t \r \n "));
    }

    @Test
    void isBlankWithNonEmptyString() {
        assertFalse(StringUtils.isBlank("dataone"));
    }

    @Test
    void isBlankWithWhitespaceAndTextString() {
        assertFalse(StringUtils.isBlank(" dataone "));
    }
}

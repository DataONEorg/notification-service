package org.dataone.notifications.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilsTest {

    @Test
    void isBlank_null() {
        assertTrue(StringUtils.isBlank(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n", "    ", " \t \t \r \n "})
    void isBlank_true(String testString) {
        assertTrue(StringUtils.isBlank(testString));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataone", " dataone ", "\n dataone\t", " dataone\n"})
    void isBlank_false() {
        assertFalse(StringUtils.isBlank("dataone"));
    }
}

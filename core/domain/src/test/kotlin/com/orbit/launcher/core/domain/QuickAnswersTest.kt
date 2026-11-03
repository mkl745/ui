package com.orbit.launcher.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAnswersTest {
    @Test fun arithmeticPrecedenceAndParentheses() {
        assertEquals("14", quickAnswer("2 + 3 * 4"))
        assertEquals("20", quickAnswer("(2 + 3) * 4"))
        assertEquals("-2", quickAnswer("-5 + 3"))
    }
    @Test fun conversionAndValidation() {
        assertEquals("1.609344 km", quickAnswer("1 mi to km"))
        assertEquals("32 f", quickAnswer("0 c to f"))
        assertNull(quickAnswer("1 kg to km"))
        assertNull(quickAnswer("1 / 0"))
        assertNull(quickAnswer("2 + nope"))
        assertNull(quickAnswer("123"))
    }
}

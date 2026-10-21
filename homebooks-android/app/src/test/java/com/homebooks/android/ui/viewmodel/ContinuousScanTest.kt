package com.homebooks.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuousScanTest {

    @Test
    fun testIsbnNormalizationAndValidation() {
        val normalizeAndValidate = { raw: String ->
            if (raw.isBlank()) null
            else {
                val cleaned = raw.replace(Regex("[^0-9Xx]"), "").uppercase()
                if (cleaned.length in 8..14) cleaned else null
            }
        }

        assertEquals("9783161484100", normalizeAndValidate("978-3-16-148410-0"))
        assertEquals("0321125215", normalizeAndValidate("0-321-12521-5"))
        assertEquals("032112521X", normalizeAndValidate("0-321-12521-X"))
        assertNull(normalizeAndValidate("short"))
        assertNull(normalizeAndValidate(""))
    }
}

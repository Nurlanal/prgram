package az.market.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun normalizesCommonPhoneFormats() {
        listOf("050 123 45 67", "0501234567", "50 123 45 67", "+994 50 123 45 67", "994501234567", "(050) 123-45-67")
            .forEach { assertEquals(it, "+994501234567", AzPhone.normalize(it)) }
        assertEquals("+994101234567", AzPhone.normalize("010 123 45 67"))
        assertEquals("+994997654321", AzPhone.normalize("099 765 43 21"))
    }

    @Test fun rejectsNonMobileOrMalformedNumbers() {
        listOf("", "12345", "012 123 45 67" /* Baku landline */, "+7 900 123 45 67", "+994 50 123 45 6", "050 123 45 678", "abc")
            .forEach { assertNull(it, AzPhone.normalize(it)) }
    }

    @Test fun formatsForDisplay() {
        assertEquals("+994 50 123 45 67", AzPhone.format("+994501234567"))
    }

    @Test fun passwordPolicy() {
        assertEquals(PasswordPolicy.Problem.TOO_SHORT, PasswordPolicy.check("abc123"))
        assertEquals(PasswordPolicy.Problem.NEEDS_DIGIT, PasswordPolicy.check("abcdefgh"))
        assertEquals(PasswordPolicy.Problem.NEEDS_LETTER, PasswordPolicy.check("12345678"))
        assertEquals(PasswordPolicy.Problem.TOO_LONG, PasswordPolicy.check("a1".repeat(40)))
        assertNull(PasswordPolicy.check("Market2026"))
        assertNull(PasswordPolicy.check("şəkər123"))   // Azerbaijani letters count as letters
    }

    @Test fun otherValidators() {
        assertTrue(Validators.isValidEmail("admin@market.az"))
        assertFalse(Validators.isValidEmail("admin@market"))
        assertTrue(Validators.isValidOtp("123456"))
        assertFalse(Validators.isValidOtp("12345"))
        assertFalse(Validators.isValidName("   "))
        assertTrue(Validators.isValidAddress("Nizami küç. 12"))
    }
}

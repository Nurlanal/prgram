package az.market.app.core.common

/**
 * Azerbaijani mobile numbers → E.164 (+994XXXXXXXXX). Accepts the forms people actually type:
 * "050 123 45 67", "0501234567", "50 123 45 67", "+994 50 123 45 67", "994501234567".
 * Returns null for anything that is not a valid AZ mobile number.
 */
object AzPhone {
    private val MOBILE_PREFIXES = setOf("10", "50", "51", "55", "60", "70", "77", "99")

    fun normalize(input: String): String? {
        val digits = input.filter(Char::isDigit)
        val national = when {
            input.trim().startsWith("+") && digits.startsWith("994") -> digits.removePrefix("994")
            digits.length == 12 && digits.startsWith("994") -> digits.removePrefix("994")
            digits.length == 10 && digits.startsWith("0") -> digits.substring(1)
            digits.length == 9 -> digits
            else -> return null
        }
        if (national.length != 9 || national.take(2) !in MOBILE_PREFIXES) return null
        return "+994$national"
    }

    /** "+994501234567" → "+994 50 123 45 67" */
    fun format(e164: String): String {
        val n = e164.removePrefix("+994")
        if (n.length != 9) return e164
        return "+994 ${n.substring(0, 2)} ${n.substring(2, 5)} ${n.substring(5, 7)} ${n.substring(7, 9)}"
    }
}

/** Mirrors the Supabase Auth password policy configured for the project (docs/BACKEND_SETUP.md §4). */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 72 // bcrypt limit

    enum class Problem { TOO_SHORT, TOO_LONG, NEEDS_LETTER, NEEDS_DIGIT }

    fun check(password: String): Problem? = when {
        password.length < MIN_LENGTH -> Problem.TOO_SHORT
        password.length > MAX_LENGTH -> Problem.TOO_LONG
        password.none(Char::isLetter) -> Problem.NEEDS_LETTER
        password.none(Char::isDigit) -> Problem.NEEDS_DIGIT
        else -> null
    }
}

object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val OTP = Regex("^[0-9]{6}$")

    fun isValidName(value: String) = value.trim().length in 1..80
    fun isValidEmail(value: String) = EMAIL.matches(value.trim())
    fun isValidOtp(value: String) = OTP.matches(value.trim())
    fun isValidAddress(value: String) = value.trim().length in 3..300
}

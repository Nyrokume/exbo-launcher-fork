package dev.launcherfork.domain

import java.time.LocalDate
import java.time.Period

/** A field of the registration form (for validation errors). */
enum class RegistrationField { EMAIL, USERNAME, PASSWORD, LICENSE }

/**
 * Client-side rules of the age gate and the registration form (stalzone.com/register look, launcher rules):
 *  - age: at least [MIN_AGE] full years on the day of registration;
 *  - e-mail: `local@domain.tld`, no spaces, at most [EMAIL_MAX] characters (the original launcher's limit);
 *  - username: [USERNAME_MIN]..[USERNAME_MAX] characters, Latin letters, digits and `_`;
 *  - password: at least [PASSWORD_MIN] characters;
 *  - the license agreement checkbox is required, the newsletter one is not.
 */
object RegistrationRules {
    /**
     * 18: the EXBO license agreement is accepted by adults (the site's age gate gives no number); nothing in the
     * launcher code implies another age. One constant, so changing it is a one-line edit.
     */
    const val MIN_AGE = 18
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 16
    const val PASSWORD_MIN = 8
    const val EMAIL_MAX = 50
    /** Oldest year in the age gate's year list: [today.year] - [YEARS_BACK]. */
    const val YEARS_BACK = 100

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    private val USERNAME = Regex("^[A-Za-z0-9_]{$USERNAME_MIN,$USERNAME_MAX}$")

    fun isEmailValid(email: String): Boolean = email.length <= EMAIL_MAX && EMAIL.matches(email)
    fun isUsernameValid(username: String): Boolean = USERNAME.matches(username)
    fun isPasswordValid(password: String): Boolean = password.length >= PASSWORD_MIN

    /** Full years between [birth] and [today] (negative for a date in the future). */
    fun age(birth: LocalDate, today: LocalDate): Int =
        if (birth.isAfter(today)) -Period.between(today, birth).years - 1 else Period.between(birth, today).years

    fun isOldEnough(birth: LocalDate, today: LocalDate): Boolean = age(birth, today) >= MIN_AGE

    /** Days of [month] (1..12) in [year]; a year that is not chosen yet counts as a leap year (29 February stays). */
    fun daysIn(month: Int, year: Int?): Int = java.time.YearMonth.of(year ?: 2000, month).lengthOfMonth()

    /** The date, or null if it does not exist (31 April) or a part is missing. */
    fun dateOf(day: Int?, month: Int?, year: Int?): LocalDate? {
        if (day == null || month == null || year == null) return null
        if (month !in 1..12 || day !in 1..daysIn(month, year)) return null
        return LocalDate.of(year, month, day)
    }

    /** Years for the age gate, newest first. */
    fun years(today: LocalDate): List<Int> = (today.year downTo today.year - YEARS_BACK).toList()

    /**
     * Invalid fields in form order (empty = the form may be sent). The trimmed e-mail and username are checked;
     * the password is taken as typed.
     */
    fun validate(email: String, username: String, password: String, licenseAccepted: Boolean): List<RegistrationField> =
        buildList {
            if (!isEmailValid(email.trim())) add(RegistrationField.EMAIL)
            if (!isUsernameValid(username.trim())) add(RegistrationField.USERNAME)
            if (!isPasswordValid(password)) add(RegistrationField.PASSWORD)
            if (!licenseAccepted) add(RegistrationField.LICENSE)
        }
}

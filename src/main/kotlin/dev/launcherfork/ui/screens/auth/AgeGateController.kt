package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.RegistrationRules
import dev.launcherfork.ui.UiContext
import javafx.css.PseudoClass
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.util.StringConverter
import java.time.LocalDate

/**
 * age_gate_panel.fxml, registration step 1 (stalzone.com/register "HOW OLD ARE YOU?"): day / month / year.
 *  - ПРОДОЛЖИТЬ is grey until all three are chosen; the day list follows the month and year (no 31 April);
 *  - younger than [RegistrationRules.MIN_AGE] -> error under the dropdowns (they turn red until changed);
 *  - old enough -> registration form with the date; back link / Esc -> login.
 */
class AgeGateController(ctx: UiContext) : AuthScreenBase(ctx) {
    @FXML lateinit var dayBox: ComboBox<Int>
    @FXML lateinit var monthBox: ComboBox<Int>
    @FXML lateinit var yearBox: ComboBox<Int>
    @FXML lateinit var continueButton: Button
    @FXML lateinit var supportLink: Button
    @FXML lateinit var backButton: Button

    /** "Today" for the age check and the year list; tests replace it before choosing a date. */
    var today: LocalDate = LocalDate.now()
        set(v) { field = v; yearBox.items.setAll(RegistrationRules.years(v)) }

    private val boxes get() = listOf(dayBox, monthBox, yearBox)

    @FXML
    fun initialize() {
        initChrome()
        val months = ctx.t("fork.age.months").split(',').map(String::trim)
        monthBox.converter = object : StringConverter<Int>() {
            override fun toString(m: Int?): String = m?.let { months.getOrNull(it - 1) } ?: ""
            override fun fromString(s: String?): Int? = months.indexOf(s).takeIf { it >= 0 }?.plus(1)
        }
        monthBox.items.setAll((1..12).toList())
        yearBox.items.setAll(RegistrationRules.years(today))
        refreshDays()
        monthBox.valueProperty().addListener { _, _, _ -> refreshDays() }
        yearBox.valueProperty().addListener { _, _, _ -> refreshDays() }
        continueButton.disableProperty().bind(
            dayBox.valueProperty().isNull.or(monthBox.valueProperty().isNull).or(yearBox.valueProperty().isNull)
        )
        boxes.forEach { box -> box.valueProperty().addListener { _, _, _ -> showError(null) } }
        errorFirst.textProperty().addListener { _, _, text -> boxes.forEach { it.pseudoClassStateChanged(ERROR, !text.isNullOrEmpty()) } }
        listOf(errorFirst, errorSecond).forEach { it.managedProperty().bind(it.textProperty().isNotEmpty) }
        onEscape(::onBack)
    }

    /** Day list 1..days of the chosen month (any month: 31); a day that no longer exists is cleared. */
    private fun refreshDays() {
        val max = monthBox.value?.let { RegistrationRules.daysIn(it, yearBox.value) } ?: 31
        if (dayBox.items.size == max) return
        val day = dayBox.value
        dayBox.items.setAll((1..max).toList())
        dayBox.value = day?.takeIf { it <= max }
    }

    /** Pre-selects [date] (back from the registration form). */
    fun preset(date: LocalDate) {
        yearBox.value = date.year
        monthBox.value = date.monthValue
        dayBox.value = date.dayOfMonth
    }

    /** The chosen date (null while a part is missing). */
    val birthDate: LocalDate? get() = RegistrationRules.dateOf(dayBox.value, monthBox.value, yearBox.value)

    @FXML
    fun onContinue() {
        if (continueButton.isDisabled) return
        val date = birthDate ?: return
        if (!RegistrationRules.isOldEnough(date, today)) {
            return showError(ctx.t("fork.age.too_young", RegistrationRules.MIN_AGE))
        }
        ctx.navigator.showRegister(date)
    }

    /** "Нужна помощь? Свяжитесь с поддержкой": no browser in the fork, a grey note instead of an error. */
    @FXML fun onSupport() = showError(null, ctx.t("fork.not_available"))

    @FXML fun onBack() = ctx.navigator.showAuth()

    private companion object {
        val ERROR: PseudoClass = PseudoClass.getPseudoClass("error")
    }
}

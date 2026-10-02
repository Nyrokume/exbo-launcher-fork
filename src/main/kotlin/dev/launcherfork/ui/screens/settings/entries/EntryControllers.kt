package dev.launcherfork.ui.screens.settings.entries

import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.brightenOnHover
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.text.Text

/** settings/check_box_entry.fxml */
class CheckBoxEntryController {
    @FXML lateinit var checkBox: CheckBox
    @FXML lateinit var label: Label
    private var onChange: (Boolean) -> Unit = {}

    fun bind(caption: String, value: Boolean, onChange: (Boolean) -> Unit) {
        label.text = caption
        checkBox.isSelected = value
        this.onChange = onChange
    }

    @FXML fun onSelected() = onChange(checkBox.isSelected)

    @FXML
    fun onLabelClicked() {
        checkBox.isSelected = !checkBox.isSelected
        onSelected()
    }
}

/** settings/numeric_entry.fxml */
class NumericEntryController {
    @FXML lateinit var input: TextField
    @FXML lateinit var label: Label

    fun bind(caption: String, value: Int, onChange: (Int) -> Unit) {
        label.text = caption
        input.textFormatter = TextFormatter<String> { c -> if (c.controlNewText.length <= 7 && c.controlNewText.all(Char::isDigit)) c else null }
        input.text = value.toString()
        input.textProperty().addListener { _, _, t -> onChange(t.toIntOrNull() ?: 0) }
    }
}

/** settings/progression_entry.fxml: RAM slider (click/drag on the track). */
class ProgressionEntryController {
    @FXML lateinit var label: Label
    @FXML lateinit var progress: Pane
    @FXML lateinit var bar: Region
    @FXML lateinit var description: Label

    private var value = 0.0
    private var format: (Double) -> String = { "" }
    private var onChange: (Double) -> Unit = {}

    @FXML
    fun initialize() {
        progress.widthProperty().addListener { _, _, _ -> render() }
        val handler = { e: MouseEvent ->
            value = (e.x / progress.width).coerceIn(0.0, 1.0)
            render()
            onChange(value)
        }
        progress.setOnMousePressed(handler)
        progress.setOnMouseDragged(handler)
    }

    fun bind(caption: String, value: Double, format: (Double) -> String, onChange: (Double) -> Unit) {
        label.text = caption
        this.value = value
        this.format = format
        this.onChange = onChange
        render()
    }

    private fun render() {
        bar.resize(progress.width * value, progress.height.takeIf { it > 0 } ?: 13.0)
        bar.prefWidth = progress.prefWidth * value
        description.text = format(value)
    }
}

/** settings/radio_entry.fxml: options are check boxes with a round mark (as in the original radio.css). */
class RadioEntryController {
    @FXML lateinit var panel: Pane
    @FXML lateinit var label: Text

    fun bind(caption: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) {
        label.text = caption
        val boxes = options.mapIndexed { i, _ ->
            CheckBox().apply {
                styleClass += "radio-check"
                layoutX = 0.0
                layoutY = 20.0 * i + 23
                isSelected = i == selected
            }
        }
        val texts = options.mapIndexed { i, name ->
            Text(name).apply {
                styleClass += "settings-option"
                layoutX = 20.0
                layoutY = 20.0 * i + 36
            }
        }
        fun choose(i: Int) {
            boxes.forEachIndexed { j, b -> b.isSelected = i == j }
            onChange(i)
        }
        boxes.forEachIndexed { i, b -> b.setOnAction { choose(i) } }
        texts.forEachIndexed { i, t -> t.setOnMouseClicked { choose(i) } }
        panel.children.addAll(boxes + texts)
        panel.prefHeight = 20.0 * options.size + 30
    }
}

/** settings/path_entry.fxml: game root folder (folder picker is a stub in the fork). */
class PathEntryController(private val ctx: UiContext) {
    @FXML lateinit var label: TextField
    @FXML lateinit var button: Button
    @FXML lateinit var caption: Label
    var onChooseFolder: () -> Unit = {}

    @FXML
    fun initialize() {
        button.brightenOnHover()
        button.setOnAction { onChooseFolder() }
    }

    fun bind(captionText: String, path: String) {
        caption.text = captionText
        label.text = path
    }
}

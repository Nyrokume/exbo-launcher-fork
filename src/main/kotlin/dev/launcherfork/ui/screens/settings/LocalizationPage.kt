package dev.launcherfork.ui.screens.settings

import dev.launcherfork.stalzone.ItemCategory
import dev.launcherfork.stalzone.ItemKind
import dev.launcherfork.stalzone.ItemName
import dev.launcherfork.stalzone.ItemNames
import dev.launcherfork.stalzone.ItemRename
import dev.launcherfork.ui.UiContext
import javafx.beans.binding.Bindings
import javafx.application.Platform
import javafx.event.Event
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.OverrunStyle
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Localization page: search the game's item and weapon names and write the ones you change.
 * The stock name stays on the left. The field is the new name. Color and rank are written
 * in front of that name as the formatting code the game uses to tint it.
 * Rows are grouped by [ItemCategory]. The category box narrows the list to one group.
 */
class LocalizationPage(
    private val ctx: UiContext,
    private val weapons: RadioButton,
    private val items: RadioButton,
    private val changed: RadioButton,
    private val categories: ComboBox<LocCategoryOption>,
    private val search: TextField,
    private val write: Button,
    private val list: ListView<LocLine>,
    private val note: Label,
) {
    private val all = ArrayList<ItemName>()
    private val saved = LinkedHashMap<String, ItemRename>()
    private val edited = LinkedHashMap<String, String>()
    private val editedColors = LinkedHashMap<String, String?>()
    private val editedRanks = LinkedHashMap<String, String?>()
    private val colorChoices = ArrayList<Choice>()
    private val rankChoices = ArrayList<Choice>()
    private var langPath: Path? = null
    private var started = false
    private var suppressCategory = false
    @Volatile private var busy = false
    @Volatile private var pendingReload = false

    fun bind() {
        colorChoices += Choice(null, ctx.t("settings.loc.color"), null)
        colorChoices += ItemNames.nameColors.map { Choice(it.id, ctx.t("settings.loc.color.${it.id}"), it.swatch) }
        rankChoices += Choice(null, ctx.t("settings.loc.rank"), null)
        rankChoices += ItemNames.nameRanks.map { rank ->
            val swatch = ItemNames.nameColors.first { it.id == rank.colorId }.swatch
            Choice(rank.id, ctx.t("settings.loc.rank.${rank.id}"), swatch)
        }
        list.fixedCellSize = 42.0
        list.cellFactory = javafx.util.Callback { NameCell() }
        list.placeholder = Label(ctx.t("settings.loc.empty")).apply { styleClass += "settings-note" }
        categories.visibleRowCount = 14
        categories.valueProperty().addListener { _, _, _ -> if (!suppressCategory) refill() }
        weapons.toggleGroup.selectedToggleProperty().addListener { _, _, _ ->
            rebuildCategories()
            refill()
        }
        search.textProperty().addListener { _, _, _ -> refill() }
        rebuildCategories()
        write.tooltip = javafx.scene.control.Tooltip(ctx.t("settings.loc.write.hint"))
        write.isDisable = true
        write.setOnAction { commit() }
        hideNote()
        ItemNames.onRestored = { restored ->
            val mine = langPath ?: ItemNames.defaultLangFile()
            if (mine == null || sameFile(mine, restored)) requestReload()
        }
        ItemNames.defaultLangFile()?.let { file ->
            ItemNames.clientOfLang(file)?.let { ItemNames.startWatch(it) }
        }
    }

    /** First visit reads the lang file. A client update calls this again and keeps unsaved edits. */
    fun onShown() {
        if (started || !ItemNames.loadOnShow) return
        requestReload()
    }

    private fun requestReload() {
        if (!ItemNames.loadOnShow) return
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater { requestReload() }
            return
        }
        if (busy) {
            pendingReload = true
            return
        }
        reload()
    }

    private fun reload() {
        val gen = ++generation
        started = true
        if (all.isEmpty()) {
            showNote(ctx.t("settings.loc.loading"))
            write.isDisable = true
        }
        val overrides = ItemNames.overridesFile()
        thread(name = "item-names-load") {
            val loaded = runCatching {
                val path = ItemNames.defaultLangFile()
                val entries = path?.let { ItemNames.catalog(Files.readString(it)) } ?: emptyList()
                val names = runCatching { ItemNames.load(overrides) }.getOrDefault(emptyMap())
                Triple(path, entries, names)
            }
            Platform.runLater {
                if (gen != generation) return@runLater
                loaded.onSuccess { (path, entries, names) ->
                    langPath = path
                    all.clear()
                    all.addAll(entries)
                    saved.clear()
                    saved.putAll(names)
                    write.isDisable = path == null || busy
                    if (path == null) showNote(ctx.t("settings.loc.missing"))
                    else {
                        val weaponCount = entries.count { it.kind == ItemKind.WEAPON }
                        showNote(ctx.t("settings.loc.ready", weaponCount, entries.size - weaponCount))
                    }
                    list.refresh()
                    rebuildCategories()
                    refill()
                }.onFailure {
                    showNote(ctx.t("settings.loc.failed"))
                }
            }
        }
    }

    private var generation = 0

    private fun sameFile(left: Path, right: Path): Boolean =
        left.toAbsolutePath().normalize() == right.toAbsolutePath().normalize()

    private fun tabOf(item: ItemName): Boolean = when {
        weapons.isSelected -> item.kind == ItemKind.WEAPON
        items.isSelected -> item.kind == ItemKind.ITEM
        changed.isSelected -> changed(item)
        else -> false
    }

    private fun changed(item: ItemName): Boolean {
        val prior = saved[item.key]
        return prior != null || edited.containsKey(item.key) ||
            editedColors.containsKey(item.key) || editedRanks.containsKey(item.key)
    }

    private fun rebuildCategories() {
        val picked = categories.value?.category
        val counts = HashMap<ItemCategory, Int>()
        var total = 0
        for (item in all) {
            if (!tabOf(item)) continue
            total++
            val category = ItemCategory.of(item.key)
            counts[category] = (counts[category] ?: 0) + 1
        }
        val options = ArrayList<LocCategoryOption>()
        options += LocCategoryOption(null, ctx.t("settings.loc.category.all", total))
        val allowed = when {
            weapons.isSelected -> listOf(ItemCategory.GUNS, ItemCategory.MELEE)
            items.isSelected -> ItemCategory.entries.filter { it != ItemCategory.GUNS && it != ItemCategory.MELEE }
            else -> ItemCategory.entries.toList()
        }
        for (category in allowed) {
            val count = counts[category] ?: 0
            if (count == 0) continue
            options += LocCategoryOption(category, ctx.t("settings.loc.category.${category.id}", count))
        }
        suppressCategory = true
        categories.items.setAll(options)
        categories.value = options.firstOrNull { it.category == picked } ?: options.first()
        suppressCategory = false
    }

    private fun refill() {
        val category = categories.value?.category
        val query = search.text.trim().lowercase(Locale.ROOT)
        val grouped = LinkedHashMap<ItemCategory, MutableList<ItemName>>()
        for (item in all) {
            if (!tabOf(item)) continue
            val itemCategory = ItemCategory.of(item.key)
            if (category != null && itemCategory != category) continue
            val prior = saved[item.key]
            val desired = edited[item.key] ?: prior?.to ?: item.official
            val original = prior?.from ?: item.official
            if (query.isNotEmpty() &&
                !item.key.lowercase(Locale.ROOT).contains(query) &&
                !original.lowercase(Locale.ROOT).contains(query) &&
                !desired.lowercase(Locale.ROOT).contains(query)
            ) continue
            grouped.getOrPut(itemCategory) { ArrayList() }.add(item)
        }
        val lines = ArrayList<LocLine>()
        for (itemCategory in ItemCategory.entries) {
            val rows = grouped[itemCategory] ?: continue
            if (category == null) lines += LocLine.Head(ctx.t("settings.loc.category.${itemCategory.id}", rows.size))
            rows.forEach { lines += LocLine.Entry(it) }
        }
        list.items.setAll(lines)
    }

    private fun onEdit(item: ItemName, text: String) {
        val committed = saved[item.key]?.to ?: ItemNames.plain(item.official)
        if (text == committed) edited.remove(item.key) else edited[item.key] = text
    }

    private fun onColor(item: ItemName, id: String?) {
        if (id == saved[item.key]?.color) editedColors.remove(item.key) else editedColors[item.key] = id
    }

    private fun onRank(item: ItemName, id: String?) {
        if (id == saved[item.key]?.rank) editedRanks.remove(item.key) else editedRanks[item.key] = id
    }

    private fun colorOf(item: ItemName): String? =
        if (editedColors.containsKey(item.key)) editedColors[item.key] else saved[item.key]?.color

    private fun rankOf(item: ItemName): String? =
        if (editedRanks.containsKey(item.key)) editedRanks[item.key] else saved[item.key]?.rank

    private fun commit() {
        val path = langPath ?: return
        if (busy) return
        busy = true
        write.isDisable = true
        val official = all.associate { it.key to it.official }
        val previous = saved.toMap()
        val touched = edited.toMap()
        val colors = editedColors.toMap()
        val ranks = editedRanks.toMap()
        val overrides = ItemNames.overridesFile()
        thread(name = "item-names-write") {
            val result = runCatching {
                val (next, apply) = ItemNames.nextSaved(official, previous, touched, colors, ranks)
                ItemNames.writeNames(path, next, apply)
                ItemNames.save(overrides, next)
                ItemNames.clientOfLang(path)?.let { ItemNames.startWatch(it) }
                next to apply
            }
            Platform.runLater {
                busy = false
                write.isDisable = langPath == null
                result.onSuccess { (next, apply) ->
                    if (apply.isNotEmpty()) {
                        for (i in all.indices) {
                            val value = apply[all[i].key] ?: continue
                            if (all[i].official != value) all[i] = all[i].copy(official = value)
                        }
                    }
                    saved.clear()
                    saved.putAll(next)
                    edited.clear()
                    editedColors.clear()
                    editedRanks.clear()
                    refill()
                    showNote(ctx.t("settings.loc.written", next.size))
                }.onFailure {
                    showNote(ctx.t("settings.loc.failed"))
                }
                if (pendingReload) {
                    pendingReload = false
                    requestReload()
                }
            }
        }
    }

    private fun showNote(text: String) {
        note.text = text
        note.isVisible = text.isNotEmpty()
        note.isManaged = note.isVisible
    }

    private fun hideNote() = showNote("")

    private class Choice(val id: String?, val label: String, val swatch: String?) {
        override fun toString(): String = label
    }

    private inner class NameCell : ListCell<LocLine>() {
        private val head = Label().apply {
            styleClass += "loc-head"
            maxWidth = Double.MAX_VALUE
        }
        private val title = Label().apply {
            styleClass += "loc-name"
            textOverrun = OverrunStyle.ELLIPSIS
            minWidth = 0.0
        }
        private val field = TextField().apply {
            styleClass += "limit-input"
            promptText = ctx.t("settings.loc.field")
            HBox.setHgrow(this, Priority.ALWAYS)
            maxWidth = Double.MAX_VALUE
        }
        private val fieldBox = HBox(field).apply {
            styleClass.addAll("settings-field", "zone-input", "zone-radius-sm", "loc-field")
            alignment = Pos.CENTER_LEFT
            HBox.setHgrow(this, Priority.ALWAYS)
            maxWidth = Double.MAX_VALUE
        }
        private val colorBox = choiceBox(colorChoices, "loc-color") { id ->
            val item = current ?: return@choiceBox
            onColor(item, id)
            paintPreview()
        }
        private val rankBox = choiceBox(rankChoices, "loc-rank") { id ->
            val item = current ?: return@choiceBox
            onRank(item, id)
            paintPreview()
        }
        private val row = HBox(8.0, title, fieldBox, colorBox, rankBox).apply {
            styleClass += "loc-line"
            alignment = Pos.CENTER_LEFT
            val width = Bindings.max(160.0, list.widthProperty().subtract(16))
            prefWidthProperty().bind(width)
            maxWidthProperty().bind(width)
        }
        private var current: ItemName? = null
        private var writing = false

        init {
            field.textProperty().addListener { _, _, text ->
                if (writing) return@addListener
                val item = current ?: return@addListener
                onEdit(item, text)
            }
            listOf(field, colorBox, rankBox).forEach { node ->
                node.addEventFilter(ScrollEvent.SCROLL) { event ->
                    if (node is ComboBox<*> && node.isShowing) return@addEventFilter
                    Event.fireEvent(list, event.copyFor(node, list))
                    event.consume()
                }
            }
        }

        private fun choiceBox(choices: List<Choice>, kind: String, onPick: (String?) -> Unit): ComboBox<Choice> =
            ComboBox<Choice>().apply {
                styleClass.addAll("loc-choice", kind)
                items.addAll(choices)
                visibleRowCount = choices.size.coerceAtMost(8)
                value = choices.first()
                buttonCell = choiceCell()
                cellFactory = javafx.util.Callback { choiceCell() }
                valueProperty().addListener { _, _, value ->
                    if (writing) return@addListener
                    onPick(value?.id)
                }
            }

        private fun choiceCell(): ListCell<Choice> = object : ListCell<Choice>() {
            override fun updateItem(item: Choice?, empty: Boolean) {
                super.updateItem(item, empty)
                if (empty || item == null) {
                    text = null
                    style = ""
                } else {
                    text = item.label
                    style = item.swatch?.let { "-fx-text-fill: $it;" } ?: ""
                }
            }
        }

        override fun updateItem(line: LocLine?, empty: Boolean) {
            super.updateItem(line, empty)
            writing = true
            val item = (line as? LocLine.Entry)?.item
            current = item
            if (empty || line == null) {
                graphic = null
                text = null
            } else if (line is LocLine.Head) {
                head.text = line.title
                graphic = head
                text = null
                accessibleText = line.title
            } else if (item != null) {
                val prior = saved[item.key]
                title.text = prior?.from ?: ItemNames.plain(item.official)
                field.text = edited[item.key] ?: prior?.to ?: ItemNames.plain(item.official)
                select(colorBox, colorOf(item))
                select(rankBox, rankOf(item))
                paintPreview()
                graphic = row
                text = null
                accessibleText = "${title.text}. ${field.text}. ${colorBox.value?.label}. ${rankBox.value?.label}"
            }
            writing = false
        }

        private fun select(box: ComboBox<Choice>, id: String?) {
            val next = box.items.firstOrNull { it.id == id } ?: box.items.first()
            if (box.value !== next) box.value = next
        }

        private fun paintPreview() {
            val item = current ?: return
            val swatch = ItemNames.swatch(colorOf(item), rankOf(item))
            val fill = swatch?.let { "-fx-text-fill: $it;" } ?: ""
            title.style = fill
            field.style = fill
        }
    }
}

/** One row in the localization list: a category heading or an item. */
sealed interface LocLine {
    data class Head(val title: String) : LocLine
    data class Entry(val item: ItemName) : LocLine
}

/** A value in the category box. A null [category] means every category of the current tab. */
class LocCategoryOption(val category: ItemCategory?, val label: String) {
    override fun toString(): String = label
}

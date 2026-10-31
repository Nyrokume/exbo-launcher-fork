package dev.launcherfork.stalzone

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

/** One `item.*.name` line from the game lang file. Weapons are `item.wpn.*` and `item.melee.*`. */
enum class ItemKind { WEAPON, ITEM }

/**
 * The second segment of an item lang key (`item.arm.*`, `item.art.*`, …).
 * [OTHER] is every prefix that is not listed.
 */
enum class ItemCategory(val id: String) {
    GUNS("guns"),
    MELEE("melee"),
    MODS("mods"),
    ARMOR("armor"),
    PLATES("plates"),
    BAGS("bags"),
    ARTS("arts"),
    MEDS("meds"),
    FOOD("food"),
    AMMO("ammo"),
    DEVICES("devices"),
    CHARMS("charms"),
    SKINS("skins"),
    BARTER("barter"),
    QUESTS("quests"),
    CASES("cases"),
    TOYS("toys"),
    BLUEPRINTS("blueprints"),
    EXPLOSIVES("explosives"),
    DECOR("decor"),
    OTHER("other");

    companion object {
        private val prefixes = mapOf(
            GUNS to listOf("item.wpn."),
            MELEE to listOf("item.melee."),
            MODS to listOf("item.wpn_mod.", "item.att."),
            ARMOR to listOf("item.arm."),
            PLATES to listOf("item.plate."),
            BAGS to listOf("item.bag.", "item.cont.", "item.pouch."),
            ARTS to listOf("item.art."),
            MEDS to listOf("item.med."),
            FOOD to listOf("item.food."),
            AMMO to listOf("item.amm."),
            DEVICES to listOf("item.device."),
            CHARMS to listOf("item.charm."),
            SKINS to listOf("item.camo.", "item.skin.", "item.style.", "item.motif."),
            BARTER to listOf("item.brt.", "item.part."),
            QUESTS to listOf("item.quest."),
            CASES to listOf("item.lootbox.", "item.case.", "item.crate.", "item.crt.", "item.gachapon."),
            TOYS to listOf("item.ny_toys."),
            BLUEPRINTS to listOf("item.blueprint.", "item.scheme."),
            EXPLOSIVES to listOf("item.exp."),
            DECOR to listOf("item.plc."),
        )

        fun of(key: String): ItemCategory =
            entries.firstOrNull { category -> prefixes[category].orEmpty().any { key.startsWith(it) } } ?: OTHER
    }
}

data class ItemName(val key: String, val official: String, val kind: ItemKind)

/**
 * A saved rename. [from] is the stock name, [to] is the custom name, both without a color code.
 * [color] is a formatting code written in front of the name. [rank] is the item rank; its color
 * is used when [color] is empty. The game's tooltip rank line is not in the lang file, so the
 * rank is applied as that name color.
 */
data class ItemRename(
    val from: String,
    val to: String,
    val color: String? = null,
    val rank: String? = null,
)

/** One name color. [id] is the formatting code the game already uses in lang lines (`§` + [id]). */
data class ItemColor(val id: String, val swatch: String)

/** One item rank. [colorId] is the name color the game uses for that rank. */
data class ItemRank(val id: String, val colorId: String)

/**
 * Item and weapon names live in `modassets/assets/stalker/lang/<language>.lang`
 * as `key=value` lines. Overrides stay in the launcher data dir and are written
 * back into that file, including on launch, so a client update does not wipe them.
 */
object ItemNames {
    const val NAME_LIMIT = 120

    /**
     * Name colors the client draws from a leading `§` code. Ranks follow the client's
     * `core.rank.*` names: picklock gray, newbie white, stalker green, veteran blue,
     * master purple, legend red.
     */
    val nameColors: List<ItemColor> = listOf(
        ItemColor("8", "#9a9a9a"),
        ItemColor("f", "#f5f5f5"),
        ItemColor("a", "#55ff55"),
        ItemColor("9", "#5555ff"),
        ItemColor("5", "#c06cff"),
        ItemColor("c", "#ff5555"),
        ItemColor("6", "#ffaa00"),
        ItemColor("e", "#ffff55"),
        ItemColor("b", "#55ffff"),
    )

    val nameRanks: List<ItemRank> = listOf(
        ItemRank("picklock", "8"),
        ItemRank("newbie", "f"),
        ItemRank("stalker", "a"),
        ItemRank("veteran", "9"),
        ItemRank("master", "5"),
        ItemRank("legend", "c"),
    )

    private val languages = setOf("ru", "en", "es", "fr", "ko")
    private val languageKey = Regex(""""language"\s*:\s*"([A-Za-z]{2})"""")

    /**
     * Tests set this false so opening the page does not read the installed client.
     * Production leaves it true.
     */
    var loadOnShow: Boolean = true

    var overridesFile: () -> Path = { defaultOverridesFile() }

    /**
     * Fired after a client update has been parsed and saved names written back.
     * The argument is the lang file that was read. The localization page reloads from it.
     */
    var onRestored: ((Path) -> Unit)? = null

    fun kindOf(key: String): ItemKind? {
        if (!key.startsWith("item.") || !key.endsWith(".name")) return null
        return if (key.startsWith("item.wpn.") || key.startsWith("item.melee.")) ItemKind.WEAPON else ItemKind.ITEM
    }

    fun languageOf(optionsText: String): String {
        val raw = languageKey.find(optionsText)?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: return "ru"
        return raw.takeIf { it in languages } ?: "ru"
    }

    fun defaultLangFile(): Path? {
        val client = StalzonePaths.optionsFile().parent?.parent ?: return null
        return langIn(client)
    }

    /** Lang file of one installed client, or null when that client has no lang file yet. */
    fun langIn(clientDir: Path): Path? {
        val options = clientDir.resolve("config").resolve("options.json")
        val language = if (Files.isRegularFile(options)) languageOf(Files.readString(options)) else "ru"
        val file = langFile(clientDir, language)
        return file.takeIf { Files.isRegularFile(it) }
    }

    fun langFile(clientDir: Path, language: String): Path =
        clientDir.resolve("modassets").resolve("assets").resolve("stalker").resolve("lang").resolve("$language.lang")

    fun defaultOverridesFile(): Path = StalzonePaths.sessionsFile().resolveSibling("item-names.json")

    fun catalog(text: String): List<ItemName> {
        val out = ArrayList<ItemName>()
        eachLine(text) { line, _ ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@eachLine
            val key = line.substring(0, eq)
            val kind = kindOf(key) ?: return@eachLine
            out += ItemName(key, line.substring(eq + 1), kind)
        }
        return out
    }

    /** Replaces values for [values]. Other lines, including their line breaks, stay as they were. */
    fun patch(text: String, values: Map<String, String>): String {
        if (text.isEmpty() || values.isEmpty()) return text
        val clean = HashMap<String, String>(values.size)
        for ((key, raw) in values) {
            val name = writtenForm(raw)
            if (kindOf(key) == null || name == null) continue
            clean[key] = name
        }
        if (clean.isEmpty()) return text
        val out = StringBuilder(text.length + 32)
        var changed = false
        eachLine(text) { line, breakChars ->
            val eq = line.indexOf('=')
            val next = if (eq > 0) clean[line.substring(0, eq)] else null
            if (next != null && next != line.substring(eq + 1)) {
                out.append(line.substring(0, eq)).append('=').append(next)
                changed = true
            } else {
                out.append(line)
            }
            out.append(breakChars)
        }
        return if (changed) out.toString() else text
    }

    /**
     * [edited] holds only fields the user touched. A blank or stock name drops the rename
     * and, when the file still has the custom text, puts [ItemRename.from] back.
     * The second map is what [patch] should write.
     */
    fun nextSaved(
        official: Map<String, String>,
        previous: Map<String, ItemRename>,
        edited: Map<String, String>,
        colors: Map<String, String?> = emptyMap(),
        ranks: Map<String, String?> = emptyMap(),
    ): Pair<Map<String, ItemRename>, Map<String, String>> {
        val kept = LinkedHashMap<String, ItemRename>()
        val apply = LinkedHashMap<String, String>()
        val keys = LinkedHashSet<String>()
        keys.addAll(previous.keys)
        keys.addAll(edited.keys)
        keys.addAll(colors.keys)
        keys.addAll(ranks.keys)
        for (key in keys) {
            if (kindOf(key) == null) continue
            val stock = official[key] ?: continue
            val prior = previous[key]
            val stockPlain = plain(stock)
            val from = when {
                prior == null -> stockPlain
                stockPlain == prior.to || stockPlain == prior.from -> prior.from
                else -> stockPlain
            }
            val desired = sanitize(edited[key] ?: prior?.to ?: stockPlain)
            val color = knownColor(if (colors.containsKey(key)) colors[key] else prior?.color)
            val rank = knownRank(if (ranks.containsKey(key)) ranks[key] else prior?.rank)
            if (desired.isEmpty() || (desired == from && color == null && rank == null)) {
                if (stock != from) apply[key] = from
                continue
            }
            kept[key] = ItemRename(from, desired, color, rank)
            val painted = paint(desired, color, rank)
            if (stock != painted) apply[key] = painted
        }
        return kept to apply
    }

    fun load(path: Path): Map<String, ItemRename> {
        if (!Files.isRegularFile(path)) return emptyMap()
        val text = Files.readString(path).trim()
        if (text.isEmpty()) return emptyMap()
        val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyMap()
        val out = LinkedHashMap<String, ItemRename>()
        for ((rawKey, rawValue) in root) {
            val key = rawKey as? String ?: continue
            if (kindOf(key) == null) continue
            val obj = rawValue as? Map<*, *> ?: continue
            val from = sanitize(obj["from"] as? String ?: continue)
            val to = sanitize(obj["to"] as? String ?: continue)
            val color = knownColor(obj["color"] as? String)
            val rank = knownRank(obj["rank"] as? String)
            if (from.isEmpty() || to.isEmpty() || (from == to && color == null && rank == null)) continue
            out[key] = ItemRename(from, to, color, rank)
        }
        return out
    }

    fun save(path: Path, names: Map<String, ItemRename>) {
        val body = names.toSortedMap().mapValues { (_, rename) ->
            val obj = linkedMapOf("from" to rename.from, "to" to rename.to)
            if (rename.color != null) obj["color"] = rename.color
            if (rename.rank != null) obj["rank"] = rename.rank
            obj
        }
        val parent = path.parent
        if (parent != null) Files.createDirectories(parent)
        writeAtomic(path, MiniJson.write(body))
    }

    /** Patches [langFile] with [values]. A missing file or an empty map does nothing. */
    fun writeValues(langFile: Path, values: Map<String, String>) {
        if (!Files.isRegularFile(langFile) || values.isEmpty()) return
        val current = Files.readString(langFile)
        val written = patch(current, values)
        if (written != current) writeAtomic(langFile, written)
    }

    /**
     * After a client download, verify, or reinstall: read the lang file the update just wrote,
     * keep every saved name, and put those names back. A new stock name replaces [ItemRename.from].
     * A key the update removed is dropped. Missing lang file does nothing.
     */
    fun restoreAfterUpdate(clientDir: Path) {
        val lang = langIn(clientDir) ?: return
        val text = Files.readString(lang)
        val official = catalog(text).associate { it.key to it.official }
        val previous = load(overridesFile())
        val (kept, apply) = reconcileAfterUpdate(official, previous)
        if (kept != previous) save(overridesFile(), kept)
        if (apply.isNotEmpty()) writeValues(lang, apply)
        onRestored?.invoke(lang)
    }

    /**
     * [official] is the live lang file, after the client update and before names are written back.
     * The first map is what to keep. The second is what [patch] should write.
     */
    fun reconcileAfterUpdate(
        official: Map<String, String>,
        previous: Map<String, ItemRename>,
    ): Pair<Map<String, ItemRename>, Map<String, String>> {
        val kept = LinkedHashMap<String, ItemRename>()
        val apply = LinkedHashMap<String, String>()
        for ((key, rename) in previous) {
            if (kindOf(key) == null) continue
            val stock = official[key] ?: continue
            val stockPlain = plain(stock)
            val from = when {
                stockPlain == rename.to || stockPlain == rename.from -> rename.from
                else -> stockPlain
            }
            val to = sanitize(rename.to)
            val color = knownColor(rename.color)
            val rank = knownRank(rename.rank)
            if (to.isEmpty() || (to == from && color == null && rank == null)) continue
            kept[key] = ItemRename(from, to, color, rank)
            val painted = paint(to, color, rank)
            if (stock != painted) apply[key] = painted
        }
        return kept to apply
    }

    /** Writes saved names into [langFile]. Missing files and an empty override list do nothing. */
    fun applySaved(langFile: Path, overridesFile: Path = this.overridesFile()) {
        if (!Files.isRegularFile(langFile) || !Files.isRegularFile(overridesFile)) return
        val names = load(overridesFile)
        if (names.isEmpty()) return
        val text = Files.readString(langFile)
        val patched = patch(text, names.mapValues { (_, rename) -> paint(rename.to, rename.color, rename.rank) })
        if (patched == text) return
        writeAtomic(langFile, patched)
    }

    /** Drops a leading `§` color the game uses to tint a name. */
    fun plain(value: String): String {
        var text = value
        while (text.length >= 2 && text[0] == '§') text = text.substring(2)
        return text
    }

    /** Writes [color], or the rank's color when [color] is empty, in front of the plain name. */
    fun paint(name: String, color: String?, rank: String?): String {
        val body = sanitize(plain(name))
        val code = knownColor(color) ?: nameRanks.firstOrNull { it.id == knownRank(rank) }?.colorId
        return if (code == null || body.isEmpty()) body else "§$code$body"
    }

    fun swatch(color: String?, rank: String?): String? {
        val code = knownColor(color) ?: nameRanks.firstOrNull { it.id == knownRank(rank) }?.colorId ?: return null
        return nameColors.firstOrNull { it.id == code }?.swatch
    }

    fun sanitize(raw: String?): String {
        if (raw == null) return ""
        return raw.replace("\r", "").replace("\n", "").replace("§", "").trim().take(NAME_LIMIT)
    }

    private fun knownColor(id: String?): String? = id?.takeIf { nameColors.any { color -> color.id == it } }

    private fun knownRank(id: String?): String? = id?.takeIf { nameRanks.any { rank -> rank.id == it } }

    private fun writtenForm(raw: String): String? {
        val body = sanitize(plain(raw))
        if (body.isEmpty()) return null
        val code = if (raw.length >= 2 && raw[0] == '§') knownColor(raw.substring(1, 2)) else null
        return if (code == null) body else "§$code$body"
    }

    private fun writeAtomic(path: Path, text: String) {
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(tmp, text)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun eachLine(text: String, block: (line: String, breakChars: String) -> Unit) {
        var i = 0
        while (i < text.length) {
            var end = i
            while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
            val line = text.substring(i, end)
            val breakStart = end
            if (end < text.length && text[end] == '\r') end++
            if (end < text.length && text[end] == '\n') end++
            block(line, text.substring(breakStart, end))
            if (end == i) return
            i = end
        }
    }
}

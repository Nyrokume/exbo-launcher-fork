package dev.launcherfork

import dev.launcherfork.stalzone.ItemCategory
import dev.launcherfork.stalzone.ItemKind
import dev.launcherfork.stalzone.ItemNames
import dev.launcherfork.stalzone.ItemRename
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ItemNamesTest {
    private val lang = """
        item.wpn.kedr.name=ПП-91 «Кедр»
        item.melee.knife.name=Нож
        item.wpn_mod.scope.name=Прицел
        item.food.bread.name=Хлеб
        gui.menu.name=Меню
    """.trimIndent() + "\n"

    @Test
    fun weaponsAreGunsAndMelee() {
        assertEquals(ItemKind.WEAPON, ItemNames.kindOf("item.wpn.kedr.name"))
        assertEquals(ItemKind.WEAPON, ItemNames.kindOf("item.melee.knife.name"))
        assertEquals(ItemKind.ITEM, ItemNames.kindOf("item.wpn_mod.scope.name"))
        assertEquals(ItemKind.ITEM, ItemNames.kindOf("item.food.bread.name"))
        assertNull(ItemNames.kindOf("gui.menu.name"))
        assertNull(ItemNames.kindOf("item.wpn.kedr.description"))
    }

    @Test
    fun categoriesFollowTheKeyPrefix() {
        assertEquals(ItemCategory.GUNS, ItemCategory.of("item.wpn.kedr.name"))
        assertEquals(ItemCategory.MELEE, ItemCategory.of("item.melee.knife.name"))
        assertEquals(ItemCategory.MODS, ItemCategory.of("item.wpn_mod.scope.name"))
        assertEquals(ItemCategory.MODS, ItemCategory.of("item.att.scope.name"))
        assertEquals(ItemCategory.FOOD, ItemCategory.of("item.food.bread.name"))
        assertEquals(ItemCategory.ARTS, ItemCategory.of("item.art.kisel_vitok.name"))
        assertEquals(ItemCategory.OTHER, ItemCategory.of("item.filler.stub.name"))
    }

    @Test
    fun catalogKeepsItemAndWeaponNames() {
        val names = ItemNames.catalog(lang)
        assertEquals(listOf("item.wpn.kedr.name", "item.melee.knife.name", "item.wpn_mod.scope.name", "item.food.bread.name"), names.map { it.key })
        assertEquals("ПП-91 «Кедр»", names.first().official)
        assertEquals(2, names.count { it.kind == ItemKind.WEAPON })
    }

    @Test
    fun patchReplacesOnlyTheNamedLineAndKeepsBreaks() {
        val patched = ItemNames.patch(lang.replace("\n", "\r\n"), mapOf("item.wpn.kedr.name" to "Кедр"))
        assertTrue(patched.contains("item.wpn.kedr.name=Кедр\r\n"))
        assertTrue(patched.contains("item.food.bread.name=Хлеб\r\n"))
        assertTrue(patched.contains("gui.menu.name=Меню\r\n"))
        assertEquals(lang.replace("\n", "\r\n"), ItemNames.patch(lang.replace("\n", "\r\n"), mapOf("item.wpn.kedr.name" to "ПП-91 «Кедр»")))
    }

    @Test
    fun blankAndForeignKeysAreNotWritten() {
        val patched = ItemNames.patch(lang, mapOf("gui.menu.name" to "Нет", "item.food.bread.name" to "  "))
        assertEquals(lang, patched)
    }

    @Test
    fun nextSavedRestoresTheStockName() {
        val official = mapOf("item.wpn.kedr.name" to "Кедр")
        val previous = mapOf("item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр"))
        val (kept, apply) = ItemNames.nextSaved(official, previous, mapOf("item.wpn.kedr.name" to "ПП-91 «Кедр»"))
        assertTrue(kept.isEmpty())
        assertEquals("ПП-91 «Кедр»", apply["item.wpn.kedr.name"])
    }

    @Test
    fun nextSavedKeepsANewName() {
        val official = mapOf("item.food.bread.name" to "Хлеб")
        val (kept, apply) = ItemNames.nextSaved(official, emptyMap(), mapOf("item.food.bread.name" to "Булка"))
        assertEquals(ItemRename("Хлеб", "Булка"), kept["item.food.bread.name"])
        assertEquals("Булка", apply["item.food.bread.name"])
    }

    @Test
    fun overridesRoundTripAndApplyOntoTheLangFile() {
        val dir = Files.createTempDirectory("item-names")
        try {
            val langFile = dir.resolve("ru.lang")
            val overrides = dir.resolve("item-names.json")
            Files.writeString(langFile, lang)
            ItemNames.save(overrides, mapOf("item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр")))
            assertEquals("Кедр", ItemNames.load(overrides).getValue("item.wpn.kedr.name").to)
            ItemNames.applySaved(langFile, overrides)
            assertTrue(Files.readString(langFile).contains("item.wpn.kedr.name=Кедр\n"))
            ItemNames.applySaved(langFile, overrides)
            assertTrue(Files.readString(langFile).contains("item.wpn.kedr.name=Кедр\n"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun updateKeepsTheCustomNameAndANewStockName() {
        val official = mapOf(
            "item.wpn.kedr.name" to "ПП-91 «Кедр-2»",
            "item.food.bread.name" to "Хлеб",
        )
        val previous = mapOf(
            "item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр"),
            "item.food.bread.name" to ItemRename("Хлеб", "Булка"),
            "item.wpn.gone.name" to ItemRename("Старое", "Моё"),
        )
        val (kept, apply) = ItemNames.reconcileAfterUpdate(official, previous)
        assertEquals(ItemRename("ПП-91 «Кедр-2»", "Кедр"), kept["item.wpn.kedr.name"])
        assertEquals("Кедр", apply["item.wpn.kedr.name"])
        assertEquals(ItemRename("Хлеб", "Булка"), kept["item.food.bread.name"])
        assertEquals("Булка", apply["item.food.bread.name"])
        assertEquals(null, kept["item.wpn.gone.name"])
    }

    @Test
    fun restoreAfterUpdateParsesTheNewFileAndWritesNamesBack() {
        val dir = Files.createTempDirectory("item-names-update")
        val previous = ItemNames.overridesFile
        val listener = ItemNames.onRestored
        try {
            val client = dir.resolve("client")
            val lang = client.resolve("modassets").resolve("assets").resolve("stalker").resolve("lang").resolve("ru.lang")
            Files.createDirectories(lang.parent)
            Files.writeString(lang, "item.wpn.kedr.name=ПП-91 «Кедр-2»\nitem.food.bread.name=Хлеб\n")
            val overrides = dir.resolve("item-names.json")
            ItemNames.save(overrides, mapOf(
                "item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр"),
                "item.food.bread.name" to ItemRename("Хлеб", "Булка"),
            ))
            ItemNames.overridesFile = { overrides }
            var restored: java.nio.file.Path? = null
            ItemNames.onRestored = { restored = it }
            ItemNames.restoreAfterUpdate(client)
            val text = Files.readString(lang)
            assertTrue(text.contains("item.wpn.kedr.name=Кедр\n"))
            assertTrue(text.contains("item.food.bread.name=Булка\n"))
            assertEquals("ПП-91 «Кедр-2»", ItemNames.load(overrides).getValue("item.wpn.kedr.name").from)
            assertEquals(lang, restored)
        } finally {
            ItemNames.overridesFile = previous
            ItemNames.onRestored = listener
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun colorAndRankAreWrittenInFrontOfTheName() {
        assertEquals("Кедр", ItemNames.plain("§cКедр"))
        assertEquals("Кедр", ItemNames.plain("§#ff5555Кедр"))
        assertEquals("6П62 «Толстяк»", ItemNames.plain("§#ffaa006П62 «Толстяк»"))
        assertEquals("§#ff5555Кедр", ItemNames.paint("Кедр", "c", "stalker"))
        assertEquals("§#55ff55Кедр", ItemNames.paint("Кедр", null, "stalker"))
        assertEquals("Кедр", ItemNames.paint("Кедр", null, null))
        val patched = ItemNames.patch(lang, mapOf("item.wpn.kedr.name" to "§#5555ffКедр"))
        assertTrue(patched.contains("item.wpn.kedr.name=§#5555ffКедр\n"))
    }

    @Test
    fun goldOnANameThatStartsWithSixStaysUnambiguous() {
        assertEquals("§#ffaa006П62 «Толстяк»", ItemNames.paint("6П62 «Толстяк»", "6", "picklock"))
        assertEquals("6П62 «Толстяк»", ItemNames.plain(ItemNames.paint("6П62 «Толстяк»", "6", "picklock")))
    }

    @Test
    fun colorAndRankStayWhenTheNameDoesNotChange() {
        val official = mapOf("item.wpn.kedr.name" to "ПП-91 «Кедр»")
        val (kept, apply) = ItemNames.nextSaved(
            official,
            emptyMap(),
            emptyMap(),
            colors = mapOf("item.wpn.kedr.name" to "c"),
            ranks = mapOf("item.wpn.kedr.name" to "veteran"),
        )
        assertEquals(ItemRename("ПП-91 «Кедр»", "ПП-91 «Кедр»", "c", "veteran"), kept["item.wpn.kedr.name"])
        assertEquals("§#ff5555ПП-91 «Кедр»", apply["item.wpn.kedr.name"])
    }

    @Test
    fun rankAloneUsesTheRankColor() {
        val official = mapOf("item.wpn.kedr.name" to "ПП-91 «Кедр»")
        val (kept, apply) = ItemNames.nextSaved(
            official,
            emptyMap(),
            emptyMap(),
            ranks = mapOf("item.wpn.kedr.name" to "master"),
        )
        assertEquals("master", kept["item.wpn.kedr.name"]?.rank)
        assertEquals(null, kept["item.wpn.kedr.name"]?.color)
        assertEquals("§#c06cffПП-91 «Кедр»", apply["item.wpn.kedr.name"])
    }

    @Test
    fun clearingColorRestoresThePlainStockName() {
        val official = mapOf("item.wpn.kedr.name" to "§cКедр")
        val previous = mapOf("item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр", "c", null))
        val (kept, apply) = ItemNames.nextSaved(
            official,
            previous,
            mapOf("item.wpn.kedr.name" to "ПП-91 «Кедр»"),
            colors = mapOf("item.wpn.kedr.name" to null),
        )
        assertTrue(kept.isEmpty())
        assertEquals("ПП-91 «Кедр»", apply["item.wpn.kedr.name"])
    }

    @Test
    fun updateKeepsColorAndRankAndRepaintsTheName() {
        val official = mapOf("item.wpn.kedr.name" to "ПП-91 «Кедр-2»")
        val previous = mapOf("item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр", "a", "stalker"))
        val (kept, apply) = ItemNames.reconcileAfterUpdate(official, previous)
        assertEquals(ItemRename("ПП-91 «Кедр-2»", "Кедр", "a", "stalker"), kept["item.wpn.kedr.name"])
        assertEquals("§#55ff55Кедр", apply["item.wpn.kedr.name"])
    }

    @Test
    fun colorAndRankRoundTrip() {
        val dir = Files.createTempDirectory("item-names-color")
        try {
            val overrides = dir.resolve("item-names.json")
            val saved = ItemRename("ПП-91 «Кедр»", "Кедр", "9", "veteran")
            ItemNames.save(overrides, mapOf("item.wpn.kedr.name" to saved))
            assertEquals(saved, ItemNames.load(overrides).getValue("item.wpn.kedr.name"))
            val langFile = dir.resolve("ru.lang")
            Files.writeString(langFile, lang)
            ItemNames.applySaved(langFile, overrides)
            assertTrue(Files.readString(langFile).contains("item.wpn.kedr.name=§#5555ffКедр\n"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun siblingNameKeysGetTheSamePaintedName() {
        val text = """
            item.wpn.kedr.name=ПП-91 «Кедр»
            item.wpn.kedr.short_name=Кедр
            frontline.calldown.kedr.name=ПП-91 «Кедр»
            item.food.bread.name=Хлеб
        """.trimIndent() + "\n"
        val names = mapOf("item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр", "6", "picklock"))
        val extras = ItemNames.extraValues(text, names)
        assertEquals("§#ffaa00Кедр", extras["item.wpn.kedr.short_name"])
        assertEquals("§#ffaa00Кедр", extras["frontline.calldown.kedr.name"])
        assertEquals(null, extras["item.food.bread.name"])
    }

    @Test
    fun updateWritesColorAndSiblingsBackAfterTheStockFileReturns() {
        val dir = Files.createTempDirectory("item-names-reapply")
        val previous = ItemNames.overridesFile
        val listener = ItemNames.onRestored
        try {
            val client = dir.resolve("client")
            val lang = client.resolve("modassets").resolve("assets").resolve("stalker").resolve("lang").resolve("ru.lang")
            Files.createDirectories(lang.parent)
            val stock = "item.wpn.kedr.name=ПП-91 «Кедр»\nitem.wpn.kedr.short_name=Кедр\nfrontline.calldown.kedr.name=ПП-91 «Кедр»\n"
            Files.writeString(lang, stock)
            val overrides = dir.resolve("item-names.json")
            ItemNames.save(overrides, mapOf("item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр", "6", "picklock")))
            ItemNames.overridesFile = { overrides }
            ItemNames.onRestored = null
            ItemNames.restoreAfterUpdate(client)
            val first = Files.readString(lang)
            assertTrue(first.contains("item.wpn.kedr.name=§#ffaa00Кедр\n"))
            assertTrue(first.contains("item.wpn.kedr.short_name=§#ffaa00Кедр\n"))
            assertTrue(first.contains("frontline.calldown.kedr.name=§#ffaa00Кедр\n"))
            Files.writeString(lang, stock)
            ItemNames.restoreAfterUpdate(client)
            val again = Files.readString(lang)
            assertTrue(again.contains("item.wpn.kedr.name=§#ffaa00Кедр\n"))
            assertTrue(again.contains("frontline.calldown.kedr.name=§#ffaa00Кедр\n"))
        } finally {
            ItemNames.overridesFile = previous
            ItemNames.onRestored = listener
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun restoreKeepsWritingAfterEveryLanguageFileIsReset() {
        val dir = Files.createTempDirectory("item-names-langs")
        val previous = ItemNames.overridesFile
        val listener = ItemNames.onRestored
        try {
            val client = dir.resolve("client")
            val langDir = client.resolve("modassets").resolve("assets").resolve("stalker").resolve("lang")
            Files.createDirectories(langDir)
            Files.writeString(client.resolve("config").resolve("options.json").also { Files.createDirectories(it.parent) }, """{ "language": "ru" }""")
            val ru = langDir.resolve("ru.lang")
            val en = langDir.resolve("en.lang")
            Files.writeString(ru, "item.wpn.kedr.name=ПП-91 «Кедр»\n")
            Files.writeString(en, "item.wpn.kedr.name=PP-91 Kedr\n")
            val overrides = dir.resolve("item-names.json")
            ItemNames.save(overrides, mapOf("item.wpn.kedr.name" to ItemRename("ПП-91 «Кедр»", "Кедр", "c", "legend")))
            ItemNames.overridesFile = { overrides }
            ItemNames.onRestored = null
            ItemNames.restoreAfterUpdate(client)
            assertTrue(Files.readString(ru).contains("item.wpn.kedr.name=§#ff5555Кедр\n"))
            assertTrue(Files.readString(en).contains("item.wpn.kedr.name=§#ff5555Кедр\n"))
            Files.writeString(en, "item.wpn.kedr.name=PP-91 Kedr\n")
            ItemNames.restoreAfterUpdate(client)
            assertTrue(Files.readString(en).contains("item.wpn.kedr.name=§#ff5555Кедр\n"))
        } finally {
            ItemNames.overridesFile = previous
            ItemNames.onRestored = listener
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun languageComesFromOptions() {
        assertEquals("ru", ItemNames.languageOf("""{ "language": "ru" }"""))
        assertEquals("en", ItemNames.languageOf("""{ "language": "en" }"""))
        assertEquals("ru", ItemNames.languageOf("""{ "language": "de" }"""))
    }
}

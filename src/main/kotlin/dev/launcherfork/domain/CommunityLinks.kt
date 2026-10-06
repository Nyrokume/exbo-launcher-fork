package dev.launcherfork.domain

/** One STALZONE community channel of the header's «Сообщества» dropdown. */
data class CommunityLink(
    /** Stable id: fx id suffix of the menu row and the key of its brand glyph (ui/components/BrandGlyphs). */
    val id: String,
    /** Brand name shown in the menu (not translated). */
    val title: String,
    val url: String,
    /** Former / alternative name for screen readers, appended in parentheses ("Title (aka)"). */
    val aka: String? = null,
) {
    val accessibleName: String get() = if (aka == null) title else "$title ($aka)"
}

/** The ONE place with the community URLs; the menu shows them in this order. */
object CommunityLinks {
    val DISCORD = CommunityLink("discord", "Discord", "https://discord.gg/stalzone")
    val YOUTUBE = CommunityLink("youtube", "YouTube", "https://www.youtube.com/@stalzone_eng")
    val REDDIT = CommunityLink("reddit", "Reddit", "https://www.reddit.com/r/stalzone/")
    val TELEGRAM = CommunityLink("telegram", "Telegram", "https://t.me/stalzone_eng")

    val all: List<CommunityLink> = listOf(DISCORD, YOUTUBE, REDDIT, TELEGRAM)
}

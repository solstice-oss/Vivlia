package org.solsticesw.vivlia.domain.model

enum class MediaType {
    MANGA,
    MANHWA,
    MANHUA,
    NOVEL,
    ANIME,
    COMIC,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): MediaType {
            return when (value?.uppercase()) {
                "MANGA" -> MANGA
                "MANHWA" -> MANHWA
                "MANHUA" -> MANHUA
                "NOVEL", "LN", "LIGHT_NOVEL" -> NOVEL
                "ANIME" -> ANIME
                "COMIC" -> COMIC
                else -> UNKNOWN
            }
        }
    }
}

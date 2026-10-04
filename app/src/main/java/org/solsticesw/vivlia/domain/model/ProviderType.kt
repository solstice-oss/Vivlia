package org.solsticesw.vivlia.domain.model

enum class ProviderType {
    MIHON,
    LN_READER,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): ProviderType {
            return when (value?.uppercase()) {
                "MIHON", "TACHIYOMI" -> MIHON
                "LN_READER", "LNREADER" -> LN_READER
                else -> UNKNOWN
            }
        }
    }
}

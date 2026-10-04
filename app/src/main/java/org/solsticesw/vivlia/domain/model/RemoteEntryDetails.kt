package org.solsticesw.vivlia.domain.model

data class RemoteEntryDetails(
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val summary: String? = null,
    val coverUrl: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val alternativeTitles: List<String> = emptyList(),
    val mediaType: MediaType = MediaType.UNKNOWN,
    val sourceId: String
)

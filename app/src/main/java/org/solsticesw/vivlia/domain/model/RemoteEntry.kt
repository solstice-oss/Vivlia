package org.solsticesw.vivlia.domain.model

data class RemoteEntry(
    val id: String,
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val summary: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val sourceId: String,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val providerType: ProviderType = ProviderType.UNKNOWN
)

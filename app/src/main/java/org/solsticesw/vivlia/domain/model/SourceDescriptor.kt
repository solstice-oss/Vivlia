package org.solsticesw.vivlia.domain.model

data class SourceDescriptor(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val version: String = "1.0.0",
    val iconUrl: String? = null,
    val providerType: ProviderType = ProviderType.UNKNOWN,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val supportsLatest: Boolean = true,
    val isNsfw: Boolean = false
)

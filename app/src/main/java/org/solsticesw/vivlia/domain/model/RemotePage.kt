package org.solsticesw.vivlia.domain.model

data class RemotePage(
    val index: Int,
    val imageUrl: String? = null,
    val text: String? = null,
    val headers: Map<String, String> = emptyMap()
)

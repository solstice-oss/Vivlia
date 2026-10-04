package org.solsticesw.vivlia.domain.model

data class RemoteChapter(
    val url: String,
    val name: String,
    val chapterNumber: Float = -1f,
    val dateUploaded: Long = 0L,
    val scanlator: String? = null,
    val type: MediaType = MediaType.UNKNOWN
)

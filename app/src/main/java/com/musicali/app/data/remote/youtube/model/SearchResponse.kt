package com.musicali.app.data.remote.youtube.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val items: List<SearchItem> = emptyList()
)

@Serializable
data class SearchItem(
    val id: SearchItemId,
    val snippet: SearchSnippet
)

@Serializable
data class SearchItemId(
    val kind: String,
    val videoId: String = "",
    val channelId: String = ""
)

@Serializable
data class SearchSnippet(
    val title: String
)

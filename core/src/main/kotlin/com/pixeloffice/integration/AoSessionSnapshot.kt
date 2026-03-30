package com.pixeloffice.integration

data class AoSessionSnapshot(
    val id: String,
    val projectId: String,
    val status: String,
    val activity: String?,
    val attentionLevel: String?,
    val lastActivityAt: String?
)

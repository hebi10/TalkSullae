package com.hebi.talksullae.firebase

internal object ProfileResponseGuard {
    fun isCurrent(
        requestedUid: String?,
        currentUid: String?,
        requestedRoute: String,
        currentRoute: String,
        requestToken: Long,
        currentToken: Long
    ): Boolean = requestedUid != null && requestedUid == currentUid &&
        requestedRoute == currentRoute && requestToken == currentToken
}

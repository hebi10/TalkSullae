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


/** Profile read and save completions use independent request generations. */
internal class ProfileRequestVersions {
    private var loadVersion: Long = 0L
    var saveVersion: Long = 0L
        private set

    fun beginLoad(): Long {
        saveVersion++
        return ++loadVersion
    }

    fun beginSave(): Long = ++saveVersion

    fun invalidateSave() {
        saveVersion++
    }

    fun isLatestLoad(token: Long): Boolean = token == loadVersion

    fun isLatestSave(token: Long): Boolean = token == saveVersion
}

package com.hebi.talksullae.firebase

internal enum class SnapshotAccess { WAIT, HIDE, ACCEPT }

internal class ServerSnapshotGate(private val reportInitialCache: Boolean = false) {
    private var confirmed = false
    fun evaluate(fromCache: Boolean, sameAccount: Boolean = true): SnapshotAccess {
        if (!sameAccount) {
            confirmed = false
            return SnapshotAccess.HIDE
        }
        if (!fromCache) {
            confirmed = true
            return SnapshotAccess.ACCEPT
        }
        val hide = confirmed || reportInitialCache
        confirmed = false
        return if (hide) SnapshotAccess.HIDE else SnapshotAccess.WAIT
    }
}

internal fun unexpiredMessage(expiresAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean =
    expiresAtMillis == null || expiresAtMillis > nowMillis

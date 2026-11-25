package com.hebi.talksullae.firebase

internal data class SharedUnreadActivity(val epoch: Long, val sequence: Long, val ownSent: Long, val lastChatAtMillis: Long?) {
    fun count(readEpoch: Long, readSequence: Long, readOwnSent: Long, carry: Long): Long? {
        if (epoch <= 0 || sequence < 0 || ownSent !in 0..sequence || readEpoch <= 0 || readEpoch > epoch) return null
        if (readEpoch < epoch) return sequence - ownSent
        if (readSequence !in 0..sequence || readOwnSent !in 0..ownSent || carry < 0) return null
        val delta = sequence - readSequence - (ownSent - readOwnSent)
        if (delta < 0 || carry > Long.MAX_VALUE - delta) return null
        return carry + delta
    }
}

internal data class SharedUnreadBaseline(val epoch: Long, val sequence: Long, val ownSent: Long, val carry: Long)

package com.steel101.wearsyncforbreezy.sync

import java.security.MessageDigest

object SyncUtils {
    fun getHashedWatchId(androidId: String?): String {
        if (androidId.isNullOrEmpty()) return "UNKNOWN"
        return try {
            val bytes = androidId.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }.take(12).uppercase()
        } catch (e: Exception) {
            val hash = androidId.hashCode().toLong()
            (hash xor 0x5EEDL).toString(16).uppercase()
        }
    }
}

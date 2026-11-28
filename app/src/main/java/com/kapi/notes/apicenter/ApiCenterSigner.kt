package com.kapi.notes.apicenter

import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Builds the `X-Signature` dev1002-api-center expects: HMAC-SHA256 over a 6-line canonical
 * string (method, path, sorted query string, timestamp, nonce, SHA-256 hex of the raw request
 * body), keyed with the app secret. [appSecret] never leaves this process - only the resulting
 * signature (and the other already-public headers it was derived from) go over the wire. */
internal object ApiCenterSigner {

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun newNonce(): String = UUID.randomUUID().toString()

    fun newTimestamp(): String = (System.currentTimeMillis() / 1000).toString()

    fun sign(
        appSecret: String,
        method: String,
        path: String,
        sortedQueryString: String,
        timestamp: String,
        nonce: String,
        rawBody: ByteArray
    ): String {
        val canonical = listOf(method, path, sortedQueryString, timestamp, nonce, sha256Hex(rawBody))
            .joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(appSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

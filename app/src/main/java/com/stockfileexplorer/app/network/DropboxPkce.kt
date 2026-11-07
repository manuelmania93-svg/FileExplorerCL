package com.stockfileexplorer.app.network

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Public-client authorization request. No app secret is embedded or required. */
internal class DropboxPkce(private val appKey: String, private val redirectUri: String) {
    private var pending: Request? = null
    data class Request(val state: String, val verifier: String, val challenge: String, val url: String) {
        override fun toString() = "Dropbox authorization request (redacted)"
    }
    fun begin(): Request {
        require(appKey.isNotBlank() && redirectUri.isNotBlank()) { "Registered Dropbox app configuration missing" }
        fun random() = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val verifier = random()
        val challenge = challenge(verifier)
        val state = random()
        val params = linkedMapOf("client_id" to appKey, "redirect_uri" to redirectUri, "response_type" to "code", "code_challenge" to challenge,
            "code_challenge_method" to "S256", "state" to state, "token_access_type" to "offline", "scope" to "files.metadata.read files.content.read")
        val url = "https://www.dropbox.com/oauth2/authorize?" + params.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) }
        return Request(state, verifier, challenge, url).also { pending = it }
    }
    /** Single use, even when state validation fails. A callback cannot be replayed. */
    @Synchronized fun consume(returnedState: String): Request {
        val request = pending ?: error("No pending authorization")
        pending = null
        require(MessageDigest.isEqual(request.state.toByteArray(), returnedState.toByteArray())) { "Authorization state mismatch" }
        return request
    }
    fun cancel() { pending = null }
    companion object {
        fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}

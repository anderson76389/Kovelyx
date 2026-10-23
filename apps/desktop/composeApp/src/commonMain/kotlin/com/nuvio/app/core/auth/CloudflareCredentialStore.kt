package com.nuvio.app.core.auth

/** Credentials are persisted only in a platform-protected OS credential store. */
internal expect object CloudflareCredentialStore {
    fun load(): String?
    fun save(value: String)
    fun clear()
}

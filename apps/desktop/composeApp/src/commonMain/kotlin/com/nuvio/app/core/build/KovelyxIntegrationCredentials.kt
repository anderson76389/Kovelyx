package com.nuvio.app.core.build

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Built-in application IDs (when provided by release configuration) are used
 * unchanged. Desktop users can configure their OWN OAuth applications locally.
 * Never copy Nuvio's identifiers and never send credentials to GitHub.
 */
object KovelyxIntegrationCredentials {
    private var loaded = false
    private var persist: ((String, String, String) -> Unit)? = null
    private var ownTraktClientId = ""
    private var ownTraktClientSecret = ""
    private var ownMdbListClientId = ""
    private val mutableRevision = MutableStateFlow(0)
    val revision = mutableRevision.asStateFlow()

    val traktClientId: String
        get() = ownTraktClientId.ifBlank { TraktConfig.CLIENT_ID }
    val traktClientSecret: String
        get() = ownTraktClientSecret.ifBlank { TraktConfig.CLIENT_SECRET }
    val mdblistClientId: String
        get() = ownMdbListClientId.ifBlank { MdbListConfig.CLIENT_ID }
    val traktRedirectUri: String get() = TraktConfig.REDIRECT_URI

    // Called only from the Desktop entrypoint with a private, per-user store.
    fun initializeDesktop(
        clientId: String,
        clientSecret: String,
        mdbListId: String,
        onSave: (String, String, String) -> Unit,
    ) {
        if (loaded) return
        ownTraktClientId = clientId.trim()
        ownTraktClientSecret = clientSecret.trim()
        ownMdbListClientId = mdbListId.trim()
        persist = onSave
        loaded = true
        mutableRevision.value += 1
    }

    fun updateDesktop(traktId: String, traktSecret: String, mdbListId: String) {
        check(loaded && persist != null) { "Configuration réservée à Kovelyx PC" }
        val id = traktId.trim()
        val secret = traktSecret.trim()
        val mdb = mdbListId.trim()
        require((id.isBlank() && secret.isBlank()) || (id.isNotBlank() && secret.isNotBlank())) {
            "Renseignez l'identifiant et le secret Trakt ensemble."
        }
        // Persist BEFORE modifying live credentials, so failures keep old values.
        persist!!.invoke(id, secret, mdb)
        ownTraktClientId = id
        ownTraktClientSecret = secret
        ownMdbListClientId = mdb
        mutableRevision.value += 1
    }

    val desktopConfigured: Boolean get() = loaded
    val desktopTraktClientId: String get() = ownTraktClientId
    val desktopTraktSecretPresent: Boolean get() = ownTraktClientSecret.isNotBlank()
    val desktopMdbListClientId: String get() = ownMdbListClientId
}

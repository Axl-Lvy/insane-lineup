package fr.axllvy.insane.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GoTrue session payload (subset). Returned by /auth/v1/signup and /auth/v1/token. `expires_at` is
 * unix-seconds and may be missing on some endpoints — `expires_in` is always present, so we
 * recompute `expiresAtMs` ourselves on every refresh.
 */
@Serializable
data class GoTrueSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: GoTrueUser,
)

@Serializable
data class GoTrueUser(val id: String, @SerialName("is_anonymous") val isAnonymous: Boolean = false)

/** Locally-persisted session — wraps GoTrueSession + an absolute expiry timestamp. */
@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresAtMs: Long,
) {
    fun isExpired(nowMs: Long, skewMs: Long = 30_000): Boolean = nowMs + skewMs >= expiresAtMs
}

internal fun GoTrueSession.toStored(nowMs: Long): StoredSession =
    StoredSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = user.id,
        expiresAtMs = nowMs + expiresIn * 1000L,
    )

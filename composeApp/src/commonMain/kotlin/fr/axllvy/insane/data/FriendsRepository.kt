package fr.axllvy.insane.data

import fr.axllvy.insane.Config
import fr.axllvy.insane.data.auth.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A friend in the user's roster. */
@Serializable data class Friend(val id: String, val displayName: String?)

/**
 * Result of a friend code rotation: the 6-char code + when it expires (epoch ms, for countdown
 * display). Both come straight from the RPC.
 */
@Serializable data class FriendCode(val code: String, val expiresAtMs: Long)

sealed interface RedeemResult {
    data class Added(val friend: Friend) : RedeemResult

    data class Failed(val message: String) : RedeemResult
}

class FriendsRepository(
    private val http: HttpClient,
    private val session: SessionStore,
    private val nowMs: () -> Long,
) {
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _myDisplayName = MutableStateFlow<String?>(null)
    val myDisplayName: StateFlow<String?> = _myDisplayName.asStateFlow()

    /** Per-friend favorites — keyed by friend id. */
    private val _friendFavorites = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val friendFavorites: StateFlow<Map<String, Set<String>>> = _friendFavorites.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun loadAll() {
        loadMyProfile()
        loadFriends()
        loadFriendFavorites()
    }

    suspend fun loadMyProfile() {
        val me = session.userId ?: return
        val response =
            http.pgGet(session, "/rest/v1/profiles", schema = Config.INSANE_SCHEMA) {
                parameter("id", "eq.$me")
                parameter("select", "display_name")
            }
        val rows = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return
        val first = rows.firstOrNull() as? JsonObject ?: return
        _myDisplayName.value = first["display_name"]?.jsonPrimitive?.contentOrNullSafe()
    }

    /**
     * The friendships table holds (a, b) directed pairs; a SELECT scoped by RLS to my rows already
     * returns only the edges where I'm involved. We keep the rows where `a_id = me` and join to
     * profiles for display names.
     */
    suspend fun loadFriends() {
        val me = session.userId ?: return
        val response =
            http.pgGet(session, "/rest/v1/friendships", schema = Config.INSANE_SCHEMA) {
                parameter("a_id", "eq.$me")
                parameter("select", "b_id,profile:profiles!friendships_b_id_fkey(display_name)")
            }
        val rows = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return
        _friends.value = rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val id = obj["b_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val displayName =
                (obj["profile"] as? JsonObject)
                    ?.get("display_name")
                    ?.jsonPrimitive
                    ?.contentOrNullSafe()
            Friend(id = id, displayName = displayName)
        }
    }

    /** Fetch every friend's favorites (RLS scopes this to friends only). */
    suspend fun loadFriendFavorites() {
        val ids = _friends.value.map { it.id }
        if (ids.isEmpty()) {
            _friendFavorites.value = emptyMap()
            return
        }
        val inList = ids.joinToString(",", prefix = "(", postfix = ")")
        val response =
            http.pgGet(session, "/rest/v1/favorites", schema = Config.INSANE_SCHEMA) {
                parameter("user_id", "in.$inList")
                parameter("select", "user_id,fav_key")
            }
        val rows = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return
        val map = mutableMapOf<String, MutableSet<String>>()
        rows.forEach { row ->
            val obj = row as? JsonObject ?: return@forEach
            val uid = obj["user_id"]?.jsonPrimitive?.content ?: return@forEach
            val key = obj["fav_key"]?.jsonPrimitive?.content ?: return@forEach
            map.getOrPut(uid) { mutableSetOf() }.add(key)
        }
        _friendFavorites.value = map
    }

    /**
     * Generate a fresh 6-char friend code on the server. Returns null on error. The TTL is 10
     * minutes; we compute the absolute deadline client-side rather than parse the server's
     * timestamptz (avoids a date library).
     */
    suspend fun rotateCode(): FriendCode? =
        runCatching {
                val response =
                    http.pgPost(
                        session,
                        "/rest/v1/rpc/rotate_friend_code",
                        schema = Config.INSANE_SCHEMA,
                    ) {
                        setBody(buildJsonObject { /* no args */ })
                    }
                val rows =
                    json.parseToJsonElement(response.bodyAsText()) as? JsonArray
                        ?: return@runCatching null
                val first = rows.firstOrNull() as? JsonObject ?: return@runCatching null
                val code = first["code"]?.jsonPrimitive?.content ?: return@runCatching null
                FriendCode(code = code, expiresAtMs = nowMs() + 10 * 60 * 1000L)
            }
            .getOrNull()

    /** Redeem someone else's code. Adds them as a friend bidirectionally. */
    suspend fun redeem(code: String): RedeemResult {
        val cleaned = code.trim().uppercase()
        if (cleaned.length != 6) return RedeemResult.Failed("Code must be 6 characters")
        return try {
            val response =
                http.pgPost(
                    session,
                    "/rest/v1/rpc/redeem_friend_code",
                    schema = Config.INSANE_SCHEMA,
                ) {
                    setBody(buildJsonObject { put("p_code", cleaned) })
                }
            if (response.status != HttpStatusCode.OK) {
                return RedeemResult.Failed(parsePostgrestError(response.bodyAsText()))
            }
            val rows =
                json.parseToJsonElement(response.bodyAsText()) as? JsonArray
                    ?: return RedeemResult.Failed("Empty response")
            val first =
                rows.firstOrNull() as? JsonObject ?: return RedeemResult.Failed("Empty response")
            val id =
                first["friend_id"]?.jsonPrimitive?.content
                    ?: return RedeemResult.Failed("Bad response")
            val name = first["display_name"]?.jsonPrimitive?.contentOrNullSafe()
            val friend = Friend(id = id, displayName = name)
            _friends.value = _friends.value + friend
            loadFriendFavorites()
            RedeemResult.Added(friend)
        } catch (t: Throwable) {
            RedeemResult.Failed(t.message ?: "Network error")
        }
    }

    /** Update the calling user's display name. */
    suspend fun setDisplayName(name: String) {
        val me = session.userId ?: return
        val cleaned = name.trim().take(40)
        http.pgPatch(session, "/rest/v1/profiles", schema = Config.INSANE_SCHEMA) {
            parameter("id", "eq.$me")
            header("Prefer", "return=minimal")
            setBody(buildJsonObject { put("display_name", cleaned) })
        }
        _myDisplayName.value = cleaned
    }

    /** Remove a friendship in both directions. */
    suspend fun unfriend(friendId: String) {
        val me = session.userId ?: return
        // Two deletes — RLS lets me delete edges where I'm in (a, b).
        http.pgDelete(session, "/rest/v1/friendships", schema = Config.INSANE_SCHEMA) {
            parameter("a_id", "eq.$me")
            parameter("b_id", "eq.$friendId")
        }
        http.pgDelete(session, "/rest/v1/friendships", schema = Config.INSANE_SCHEMA) {
            parameter("a_id", "eq.$friendId")
            parameter("b_id", "eq.$me")
        }
        _friends.value = _friends.value.filterNot { it.id == friendId }
        _friendFavorites.value = _friendFavorites.value - friendId
    }

    private fun parsePostgrestError(body: String): String {
        val obj =
            (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject)
                ?: return body.take(120).ifBlank { "Request failed" }
        return obj["message"]?.jsonPrimitive?.contentOrNullSafe()
            ?: obj["error"]?.jsonPrimitive?.contentOrNullSafe()
            ?: body.take(120)
    }
}

/**
 * `JsonPrimitive.content` returns the literal string "null" for JSON null — we want a real Kotlin
 * null in that case.
 */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (isString) content else if (content == "null") null else content

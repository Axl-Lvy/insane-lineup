package fr.axllvy.insane.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

class FriendsRepository(private val supabase: SupabaseClient, private val nowMs: () -> Long) {
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

    private val userId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    suspend fun loadAll() {
        loadMyProfile()
        loadFriends()
        loadFriendFavorites()
    }

    suspend fun loadMyProfile() {
        val me = userId ?: return
        val row =
            supabase
                .from("profiles")
                .select(columns = Columns.list("display_name")) { filter { eq("id", me) } }
                .decodeSingleOrNull<ProfileRow>() ?: return
        _myDisplayName.value = row.display_name
    }

    /**
     * The friendships table holds (a, b) directed pairs; a SELECT scoped by RLS to my rows already
     * returns only the edges where I'm involved. We keep the rows where `a_id = me` and join to
     * profiles for display names.
     */
    suspend fun loadFriends() {
        val me = userId ?: return
        val rows =
            supabase
                .from("friendships")
                .select(
                    columns =
                        Columns.raw("b_id,profile:profiles!friendships_b_id_fkey(display_name)")
                ) {
                    filter { eq("a_id", me) }
                }
                .decodeList<FriendshipRow>()
        _friends.value = rows.map { Friend(id = it.b_id, displayName = it.profile?.display_name) }
    }

    /** Fetch every friend's favorites (RLS scopes this to friends only). */
    suspend fun loadFriendFavorites() {
        val ids = _friends.value.map { it.id }
        if (ids.isEmpty()) {
            _friendFavorites.value = emptyMap()
            return
        }
        val rows =
            supabase
                .from("favorites")
                .select(columns = Columns.list("user_id", "fav_key")) {
                    filter { isIn("user_id", ids) }
                }
                .decodeList<FriendFavRow>()
        val map = mutableMapOf<String, MutableSet<String>>()
        rows.forEach { map.getOrPut(it.user_id) { mutableSetOf() }.add(it.fav_key) }
        _friendFavorites.value = map
    }

    /**
     * Generate a fresh 6-char friend code on the server. Returns null on error. The TTL is 10
     * minutes; we compute the absolute deadline client-side rather than parse the server's
     * timestamptz (avoids a date library).
     */
    suspend fun rotateCode(): FriendCode? =
        runCatching {
                val rows = supabase.postgrest.rpc("rotate_friend_code").decodeList<RotateCodeRow>()
                val code = rows.firstOrNull()?.code ?: return@runCatching null
                FriendCode(code = code, expiresAtMs = nowMs() + 10 * 60 * 1000L)
            }
            .getOrNull()

    /** Redeem someone else's code. Adds them as a friend bidirectionally. */
    suspend fun redeem(code: String): RedeemResult {
        val cleaned = code.trim().uppercase()
        if (cleaned.length != 6) return RedeemResult.Failed("Code must be 6 characters")
        return try {
            val result =
                supabase.postgrest.rpc(
                    "redeem_friend_code",
                    buildJsonObject { put("p_code", cleaned) },
                )
            val rows = runCatching { result.decodeList<RedeemRow>() }.getOrNull()
            val first = rows?.firstOrNull() ?: return RedeemResult.Failed("Empty response")
            val friend = Friend(id = first.friend_id, displayName = first.display_name)
            _friends.value = _friends.value + friend
            loadFriendFavorites()
            RedeemResult.Added(friend)
        } catch (t: RestException) {
            RedeemResult.Failed(parsePostgrestError(t.error))
        } catch (t: Throwable) {
            RedeemResult.Failed(t.message ?: "Network error")
        }
    }

    /** Update the calling user's display name. */
    suspend fun setDisplayName(name: String) {
        val me = userId ?: return
        val cleaned = name.trim().take(40)
        supabase.from("profiles").update(buildJsonObject { put("display_name", cleaned) }) {
            filter { eq("id", me) }
        }
        _myDisplayName.value = cleaned
    }

    /** Remove a friendship in both directions. */
    suspend fun unfriend(friendId: String) {
        val me = userId ?: return
        // Two deletes — RLS lets me delete edges where I'm in (a, b).
        supabase.from("friendships").delete {
            filter {
                eq("a_id", me)
                eq("b_id", friendId)
            }
        }
        supabase.from("friendships").delete {
            filter {
                eq("a_id", friendId)
                eq("b_id", me)
            }
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

@Serializable private data class ProfileRow(val display_name: String? = null)

@Serializable private data class FriendshipRow(val b_id: String, val profile: ProfileRow? = null)

@Serializable private data class FriendFavRow(val user_id: String, val fav_key: String)

@Serializable private data class RotateCodeRow(val code: String)

@Serializable
private data class RedeemRow(
    @SerialName("friend_id") val friend_id: String,
    @SerialName("display_name") val display_name: String? = null,
)

/**
 * `JsonPrimitive.content` returns the literal string "null" for JSON null — we want a real Kotlin
 * null in that case.
 */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (isString) content else if (content == "null") null else content

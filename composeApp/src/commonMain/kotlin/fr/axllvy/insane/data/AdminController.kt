package fr.axllvy.insane.data

import fr.axllvy.insane.logE
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Resolves whether the current Supabase user has `insane.profiles.is_admin = true`. RLS shows the
 * row only when `id = auth.uid()`, so a non-admin query just decodes as `is_admin = false` (or
 * empty). Refresh after every auth state change.
 */
class AdminController(private val supabase: SupabaseClient) {
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    suspend fun refresh() {
        val me = supabase.auth.currentUserOrNull()?.id
        if (me == null) {
            _isAdmin.value = false
            return
        }
        val row =
            runCatching {
                    supabase
                        .from("profiles")
                        .select(columns = Columns.list("is_admin")) { filter { eq("id", me) } }
                        .decodeSingleOrNull<AdminRow>()
                }
                .onFailure { logE("admin: profile fetch failed: ${it.message}") }
                .getOrNull()
        _isAdmin.value = row?.is_admin == true
    }
}

@Serializable private data class AdminRow(val is_admin: Boolean = false)

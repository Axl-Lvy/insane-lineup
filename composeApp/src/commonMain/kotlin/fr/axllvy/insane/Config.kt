package fr.axllvy.insane

object Config {
    // Public Supabase project URL — same one shipped in the Axl-Lvy website
    // (NEXT_PUBLIC_SUPABASE_URL). Fill in before first run.
    const val SUPABASE_URL: String = "https://hqvnegakqcgltmrzvoxi.supabase.co"

    // Public anon (publishable) key — safe to ship in client bundles.
    // Same value as NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY in the website.
    // The insane_lineup table needs an anon SELECT policy for this to work; see README.
    const val SUPABASE_ANON_KEY: String = "sb_publishable_Nin-d5a25xxX1dW6VGXaow_jCT8p7H4"

    const val LINEUP_TABLE: String = "insane_lineup"
    const val LINEUP_ROW_ID: Int = 1

    // All insane-related tables (lineup + friends feature) live in the
    // `insane` schema. It must be in the project's "Exposed schemas" list
    // (Supabase Project Settings → API) for PostgREST to proxy it.
    const val INSANE_SCHEMA: String = "insane"

    // Backwards-compat alias for the lineup client. Kept as a separate name
    // so the lineup-vs-friends distinction stays explicit at the call site.
    const val LINEUP_SCHEMA: String = INSANE_SCHEMA
}

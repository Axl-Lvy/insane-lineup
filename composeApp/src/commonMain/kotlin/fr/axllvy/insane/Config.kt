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

    // PostgREST schema the table lives in. The Supabase project defaults to
    // "memor_chess" so we must override per-request via Accept-Profile.
    const val LINEUP_SCHEMA: String = "public"
}

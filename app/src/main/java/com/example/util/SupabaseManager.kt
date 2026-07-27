package com.example.util

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseManager {
    const val SUPABASE_URL = "https://onqvunctatkryuznewwd.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_W66t4fvJemBLY..." // Supabase publishable / anon API key

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Realtime)
        }
    }
}

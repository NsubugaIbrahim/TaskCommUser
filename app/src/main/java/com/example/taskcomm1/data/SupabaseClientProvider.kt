package com.example.taskcomm1.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseClientProvider {
    @Volatile
    private var client: SupabaseClient? = null

    fun getClient(context: Context): SupabaseClient {
        val existing = client
        if (existing != null) return existing
        synchronized(this) {
            val again = client
            if (again != null) return again
            val created = createSupabaseClient(
                supabaseUrl = System.getenv("SUPABASE_URL") ?: "https://ltlhpxweulcatwckkxkn.supabase.co",
                supabaseKey = System.getenv("SUPABASE_ANON_KEY") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx0bGhweHdldWxjYXR3Y2treGtuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTY3MDUyNDEsImV4cCI6MjA3MjI4MTI0MX0.xNvwcM-aGOfDl-5z6F14ow34lxZgetcyQL0MhawoYW0"
            ) {
                install(Auth)
                install(Postgrest)
                httpEngine = OkHttp.create()
            }
            client = created
            return created
        }
    }
}



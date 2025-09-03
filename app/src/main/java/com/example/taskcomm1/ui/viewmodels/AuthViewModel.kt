package com.example.taskcomm1.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.taskcomm1.data.SupabaseClientProvider
import com.example.taskcomm1.data.repository.TaskCommRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: TaskCommRepository
) : ViewModel() {
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val _currentUser = MutableStateFlow<Any?>(null)
    val currentUser: StateFlow<Any?> = _currentUser.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()
    
    fun init(context: Context) {
        viewModelScope.launch {
            // No-op placeholder; could collect sessionStatus similar to admin app if needed
            _authState.value = AuthState.Unauthenticated
        }
    }
    
    fun signUp(context: Context, email: String, password: String, name: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            try {
                val client = SupabaseClientProvider.getClient(context)
                val auth = client.pluginManager.getPlugin(Auth)
                val postgrest = client.pluginManager.getPlugin(Postgrest)
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                val uidAfterSignUp = auth.currentSessionOrNull()?.user?.id
                val uid = uidAfterSignUp ?: run {
                    auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    auth.currentSessionOrNull()?.user?.id
                } ?: throw Exception("Please verify your email to complete sign up")

                // Ensure profile exists as non-admin tied to uid (fail hard if it doesn't)
                try {
                    // Use upsert to ensure the profile exists or is created if missing
                    postgrest["profiles"].upsert(
                        ProfileRow(id = uid, email = email, role = "user", name = name)
                    )
                } catch (e: Exception) {
                    throw Exception("Profile creation failed: ${e.message}")
                }
                // Verify profile row exists and has role=user
                val verify = try {
                    postgrest["profiles"].select {
                        filter { eq("id", uid) }
                        limit(1)
                    }.decodeList<ProfileRow>()
                } catch (_: Exception) { emptyList() }
                val role = verify.firstOrNull()?.role?.lowercase()
                if (role != "user") {
                    throw Exception("Profile not found or invalid role")
                }
                _currentUser.value = email
                _currentUserId.value = uid
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                val msg = e.message ?: "Sign up failed"
                val friendly = if (msg.contains("email not confirmed", ignoreCase = true)) {
                    "Please confirm your email address. Check your inbox or spam folder, then try again."
                } else msg
                _authState.value = AuthState.Error(friendly)
            }
        }
    }
    
    fun signIn(context: Context, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            try {
                val client = SupabaseClientProvider.getClient(context)
                val auth = client.pluginManager.getPlugin(Auth)
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val uid = auth.currentSessionOrNull()?.user?.id
                    ?: throw Exception("Invalid credentials")
                val emailFromSession = auth.currentSessionOrNull()?.user?.email
                val postgrest = client.pluginManager.getPlugin(Postgrest)
                // Check by id
                val byId = try {
                    postgrest["profiles"].select {
                        filter { eq("id", uid) }
                        limit(1)
                    }.decodeList<ProfileRow>()
                } catch (_: Exception) { emptyList() }
                var role = byId.firstOrNull()?.role?.lowercase()
                if (role == null && !emailFromSession.isNullOrBlank()) {
                    val byEmail = try {
                        postgrest["profiles"].select {
                            filter { eq("email", emailFromSession) }
                            limit(1)
                        }.decodeList<ProfileRow>()
                    } catch (_: Exception) { emptyList() }
                    role = byEmail.firstOrNull()?.role?.lowercase()
                }
                if (role == null) {
                    try {
                        postgrest["profiles"].upsert(
                            ProfileRow(id = uid, email = emailFromSession ?: email, role = "user")
                        )
                        role = "user"
                    } catch (_: Exception) {}
                }
                if (role != "user") {
                    try { auth.signOut() } catch (_: Exception) {}
                    throw Exception("Not authorized as user")
                }
                _currentUser.value = email
                _currentUserId.value = uid
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                val msg = e.message ?: "Sign in failed"
                val friendly = if (msg.contains("email not confirmed", ignoreCase = true)) {
                    "Please confirm your email address. We just sent a verification link if you haven’t confirmed yet."
                } else msg
                _authState.value = AuthState.Error(friendly)
            }
        }
    }
    
    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                val client = SupabaseClientProvider.getClient(context)
                val auth = client.pluginManager.getPlugin(Auth)
                auth.signOut()
            } catch (_: Exception) {}
            _currentUser.value = null
            _currentUserId.value = null
            _authState.value = AuthState.Unauthenticated
        }
    }
    
    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

@Serializable
private data class ProfileRow(
    val id: String? = null,
    val email: String? = null,
    val role: String? = null,
    @SerialName("name") val name: String? = null
)


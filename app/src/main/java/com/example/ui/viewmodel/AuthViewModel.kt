package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import com.example.data.model.AllowedFamilyMembers

sealed interface AuthUiState {
    data object Unauthenticated : AuthUiState
    data object Loading : AuthUiState
    data class Authenticated(val user: FirebaseUser) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val _uiState = MutableStateFlow<AuthUiState>(
        if (auth.currentUser != null && AllowedFamilyMembers.isAllowed(auth.currentUser?.email)) AuthUiState.Authenticated(auth.currentUser!!)
        else AuthUiState.Unauthenticated
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            if (AllowedFamilyMembers.isAllowed(user.email)) {
                _uiState.value = AuthUiState.Authenticated(user)
            } else {
                auth.signOut()
                _uiState.value = AuthUiState.Error("Access Restricted: Only authorized family members (Bharathikrishna Muneeswaran, Muneeswaran Palanisamy, Deepa Muneeswaran) can access this Family Vault.")
            }
        } else {
            _uiState.value = AuthUiState.Unauthenticated
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    fun attemptAutoSignIn(context: Context) {
        if (auth.currentUser != null) {
            _uiState.value = AuthUiState.Authenticated(auth.currentUser!!)
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            _uiState.value = AuthUiState.Unauthenticated
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val credentialManager = CredentialManager.create(context)

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                } else {
                    _uiState.value = AuthUiState.Unauthenticated
                }
            } catch (e: Exception) {
                // Auto sign-in failed silently, keep unauthenticated for manual sign-in button
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            _uiState.value = AuthUiState.Error("Google Sign-In configuration missing")
            return
        }

        _uiState.value = AuthUiState.Loading

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
        val credentialManager = CredentialManager.create(activity)

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    // State listener updates to Authenticated
                } else {
                    _uiState.value = AuthUiState.Error("Unexpected credential type received")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled: ${e.message}")
                _uiState.value = AuthUiState.Unauthenticated
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In error", e)
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Sign-in failed. Please try again.")
            }
        }
    }

    fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        auth.signOut()
        viewModelScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("Auth", "Clear credential error", e)
            } finally {
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }
}

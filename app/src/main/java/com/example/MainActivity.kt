package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MasterPassphraseScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.theme.FamilyVaultTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.VaultSetupState
import com.example.ui.viewmodel.VaultViewModel

class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val vaultViewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vaultViewModel.securityManager.applyEmmWindowSecurity(this)

        setContent {
            val darkModeSetting by vaultViewModel.darkModeSetting.collectAsState()
            val authState by authViewModel.uiState.collectAsState()
            val isAppLocked by vaultViewModel.isAppLocked.collectAsState()
            val setupState by vaultViewModel.setupState.collectAsState()
            val family by vaultViewModel.currentFamily.collectAsState()

            LaunchedEffect(Unit) {
                authViewModel.attemptAutoSignIn(this@MainActivity)
            }

            LaunchedEffect(authState) {
                if (authState is AuthUiState.Authenticated) {
                    val uid = (authState as AuthUiState.Authenticated).user.uid
                    vaultViewModel.loadUserProfileAndFamily(uid)
                }
            }

            FamilyVaultTheme(darkModeSetting = darkModeSetting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (authState) {
                        is AuthUiState.Unauthenticated, is AuthUiState.Error, AuthUiState.Loading -> {
                            AuthScreen(
                                authViewModel = authViewModel,
                                authState = authState
                            )
                        }
                        is AuthUiState.Authenticated -> {
                            if (isAppLocked) {
                                PinLockScreen(vaultViewModel = vaultViewModel)
                            } else {
                                when (setupState) {
                                    VaultSetupState.LOADING -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.foundation.layout.Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(24.dp)
                                            ) {
                                                CircularProgressIndicator()
                                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                                androidx.compose.material3.Text(
                                                    "Connecting to Family Vault...",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                                                androidx.compose.material3.TextButton(
                                                    onClick = { vaultViewModel.skipLoadingToSetup() }
                                                ) {
                                                    androidx.compose.material3.Text("Skip / Setup Vault Now")
                                                }
                                            }
                                        }
                                    }
                                    VaultSetupState.NO_FAMILY, VaultSetupState.LOCKED_PASSPHRASE -> {
                                        MasterPassphraseScreen(
                                            vaultViewModel = vaultViewModel,
                                            setupState = setupState,
                                            family = family
                                        )
                                    }
                                    VaultSetupState.UNLOCKED -> {
                                        HomeScreen(
                                            vaultViewModel = vaultViewModel,
                                            authViewModel = authViewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.security.SecurityManager
import com.example.ui.theme.FamilyVaultTheme
import com.example.ui.theme.VaultAccentEmerald
import com.example.ui.theme.VaultPrimaryCyanLight

class CredentialEntryActivity : FragmentActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_CALLING_PACKAGE = "extra_calling_package"
        const val EXTRA_RP_ID = "extra_rp_id"

        const val MODE_GET_PASSWORD = "mode_get_password"
        const val MODE_GET_PASSKEY = "mode_get_passkey"
        const val MODE_SAVE_CREDENTIAL = "mode_save_credential"
    }

    private lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        securityManager = SecurityManager(this)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_GET_PASSWORD
        val callingApp = intent.getStringExtra(EXTRA_CALLING_PACKAGE) ?: "App"

        setContent {
            FamilyVaultTheme {
                CredentialPickerContent(
                    callingApp = callingApp,
                    mode = mode,
                    onAuthenticate = { pin ->
                        val valid = if (securityManager.isPinSet()) securityManager.verifyPin(pin) else true
                        if (valid) {
                            fulfillCredentialRequest()
                        }
                        valid
                    },
                    onBiometricRequest = {
                        securityManager.authenticateWithBiometrics(
                            activity = this,
                            title = "Autofill from Family Vault",
                            subtitle = "Verify to securely inject credentials for $callingApp",
                            onSuccess = { fulfillCredentialRequest() },
                            onError = {}
                        )
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    private fun fulfillCredentialRequest() {
        // Return credential result back to the calling system framework
        val resultIntent = Intent()
        // Provide standard credential payload
        resultIntent.putExtra("android.service.credentials.extra.GET_CREDENTIAL_RESPONSE", Bundle().apply {
            putString("status", "SUCCESS")
        })
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

@Composable
private fun CredentialPickerContent(
    callingApp: String,
    mode: String,
    onAuthenticate: (String) -> Boolean,
    onBiometricRequest: () -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onBiometricRequest()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (mode == CredentialEntryActivity.MODE_GET_PASSKEY) Icons.Default.VpnKey else Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (mode == CredentialEntryActivity.MODE_GET_PASSKEY) "Passkey (FIDO2) Inject" else "Family Vault Autofill",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Authenticate to autofill credentials for $callingApp",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            pin = it
                            pinError = null
                        }
                    },
                    label = { Text("4-digit Vault PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("autofill_pin_input")
                )

                if (pinError != null) {
                    Text(pinError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onBiometricRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = VaultAccentEmerald)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Biometric")
                    }

                    Button(
                        onClick = {
                            val success = onAuthenticate(pin)
                            if (!success) pinError = "Incorrect PIN"
                        },
                        modifier = Modifier.weight(1f).testTag("autofill_submit_btn")
                    ) {
                        Text("Unlock & Fill")
                    }
                }
            }
        }
    }
}

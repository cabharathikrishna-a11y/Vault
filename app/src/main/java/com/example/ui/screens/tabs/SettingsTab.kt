package com.example.ui.screens.tabs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.update.AppUpdateManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VaultAccentEmerald
import com.example.ui.theme.VaultAccentGold
import com.example.ui.theme.VaultDangerRose
import com.example.ui.theme.VaultPrimaryCyanLight
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.VaultViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    vaultViewModel: VaultViewModel,
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val user = Firebase.auth.currentUser
    val family by vaultViewModel.currentFamily.collectAsState()
    val darkModeSetting by vaultViewModel.darkModeSetting.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var newPinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var isBiometricEnabled by remember {
        mutableStateOf(vaultViewModel.securityManager.isBiometricEnabled())
    }

    var darkModeExpanded by remember { mutableStateOf(false) }
    val darkModeOptions = listOf("system" to "System Default", "dark" to "Dark Mode", "light" to "Light Mode")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Vault Settings",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Security, family access, and preferences",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        // Family Info Card
        if (family != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(VaultAccentEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = VaultAccentEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = family!!.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "${family!!.memberUids.size} Family Members",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Family Vault ID (Share with family to join):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = family!!.id,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(family!!.id))
                                Toast.makeText(context, "Family ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Family ID")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Vault Breach Audit (k-Anonymity)
        val isAuditing by vaultViewModel.isAuditingBreaches.collectAsState()
        val auditResults by vaultViewModel.auditResults.collectAsState()
        val compromisedCount = auditResults.values.count { it.isPwned }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (compromisedCount > 0) VaultDangerRose.copy(alpha = 0.15f)
                                    else VaultAccentEmerald.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (compromisedCount > 0) VaultDangerRose else VaultAccentEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Vault Breach Audit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "k-Anonymity HIBP Security Scan",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { vaultViewModel.auditVaultForBreaches() },
                        enabled = !isAuditing,
                        modifier = Modifier.testTag("run_breach_audit_button")
                    ) {
                        if (isAuditing) {
                            Text("Scanning...")
                        } else {
                            Text("Scan Vault")
                        }
                    }
                }

                if (auditResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (compromisedCount > 0) {
                        Text(
                            text = "Warning: $compromisedCount password(s) found in known public breaches. We recommend rotating them.",
                            fontSize = 12.sp,
                            color = VaultDangerRose
                        )
                    } else {
                        Text(
                            text = "All passwords safe! 0 matches found in public breach corpus.",
                            fontSize = 12.sp,
                            color = VaultAccentEmerald
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Lock & Biometrics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // PIN Setup
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App PIN Lock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (vaultViewModel.securityManager.isPinSet()) "PIN active" else "Not set",
                            fontSize = 12.sp,
                            color = if (vaultViewModel.securityManager.isPinSet()) VaultAccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.testTag("setup_pin_button")
                    ) {
                        Text(if (vaultViewModel.securityManager.isPinSet()) "Change PIN" else "Set PIN")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Biometrics Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fingerprint / Face Unlock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (vaultViewModel.securityManager.canAuthenticateBiometrics())
                                "Unlock quickly using device biometrics"
                            else "Biometric hardware not available on device",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            if (!vaultViewModel.securityManager.isPinSet() && enabled) {
                                Toast.makeText(context, "Set a PIN first to enable biometric backup", Toast.LENGTH_SHORT).show()
                            } else {
                                isBiometricEnabled = enabled
                                vaultViewModel.securityManager.setBiometricEnabled(enabled)
                            }
                        },
                        enabled = vaultViewModel.securityManager.canAuthenticateBiometrics(),
                        modifier = Modifier.testTag("biometric_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Credential Provider & Passkeys (Android 14+ CredentialManager)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VaultAccentGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = VaultAccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Credential Provider & Passkeys",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "System-level Android Credential Manager",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Transform Family Vault into your system-wide credential provider. Autofill passwords and store FIDO2 Passkeys directly inside Chrome and third-party apps.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val fallback = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                context.startActivity(fallback)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open Android Settings > Passwords & Accounts to enable", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_credential_provider_btn")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set as System Credential Provider")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance Card (Dark Mode)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Appearance",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = VaultPrimaryCyanLight)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Theme Mode", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    ExposedDropdownMenuBox(
                        expanded = darkModeExpanded,
                        onExpandedChange = { darkModeExpanded = !darkModeExpanded }
                    ) {
                        OutlinedTextField(
                            value = darkModeOptions.find { it.first == darkModeSetting }?.second ?: "System Default",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = darkModeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .width(160.dp)
                                .testTag("dark_mode_toggle")
                        )

                        ExposedDropdownMenu(
                            expanded = darkModeExpanded,
                            onDismissRequest = { darkModeExpanded = false }
                        ) {
                            darkModeOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        vaultViewModel.setDarkModeSetting(key)
                                        darkModeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Version & In-App Updates Card
        var customRtdbInput by remember { mutableStateOf("") }
        var showCustomRtdbInput by remember { mutableStateOf(false) }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                                .background(VaultPrimaryCyanLight.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = VaultPrimaryCyanLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "App Update & RTDB Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "v${AppUpdateManager.getCurrentVersionName(context)} (Build ${AppUpdateManager.getCurrentVersionCode(context)})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val customUrl = customRtdbInput.trim().ifBlank { null }
                        vaultViewModel.checkForAppUpdate(customUrl)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Updates now")
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = { showCustomRtdbInput = !showCustomRtdbInput },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = if (showCustomRtdbInput) "Hide Custom RTDB URL" else "Set Custom RTDB URL",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (showCustomRtdbInput) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customRtdbInput,
                        onValueChange = { customRtdbInput = it },
                        label = { Text("Custom RTDB / Update JSON Endpoint") },
                        placeholder = { Text("https://your-rtdb.firebaseio.com/app_update.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Account & App Reset",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = user?.displayName ?: "Signed in with Google",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = user?.email ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { authViewModel.signOut(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultDangerRose.copy(alpha = 0.15f),
                        contentColor = VaultDangerRose
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                var showSettingsResetDialog by remember { mutableStateOf(false) }

                if (showSettingsResetDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsResetDialog = false },
                        title = {
                            Text(
                                text = "Reset App & Delete Local Data?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "This will delete all locally cached vault items, master passphrase keys, PIN/biometric security settings, and sign you out to restart the app fresh.",
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showSettingsResetDialog = false
                                    vaultViewModel.resetAppAndLocalData()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete All Data & Reset App")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSettingsResetDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                TextButton(
                    onClick = { showSettingsResetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Reset App & Delete Local Data",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Set PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                newPinText = ""
                pinError = null
            },
            title = { Text("Set App Lock PIN") },
            text = {
                Column {
                    Text("Enter a 4-digit PIN to secure local access:")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                newPinText = it
                                pinError = null
                            }
                        },
                        label = { Text("4-digit PIN") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_setup_input")
                    )
                    val currentError = pinError
                    if (currentError != null) {
                        Text(currentError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinText.length == 4) {
                            vaultViewModel.securityManager.setPin(newPinText)
                            Toast.makeText(context, "PIN set successfully!", Toast.LENGTH_SHORT).show()
                            showPinDialog = false
                            newPinText = ""
                        } else {
                            pinError = "PIN must be 4 digits"
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

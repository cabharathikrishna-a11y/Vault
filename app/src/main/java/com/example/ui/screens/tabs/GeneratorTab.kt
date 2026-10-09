package com.example.ui.screens.tabs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.generator.PasswordStrength
import com.example.ui.theme.VaultAccentEmerald
import com.example.ui.theme.VaultAccentGold
import com.example.ui.theme.VaultDangerRose
import com.example.ui.theme.VaultPrimaryCyanLight
import com.example.ui.viewmodel.VaultViewModel

@Composable
fun GeneratorTab(
    vaultViewModel: VaultViewModel,
    onSaveToVault: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val state by vaultViewModel.generatorState.collectAsState()
    val config = state.config

    val strengthColor = when (state.strength) {
        PasswordStrength.WEAK -> VaultDangerRose
        PasswordStrength.FAIR -> VaultAccentGold
        PasswordStrength.STRONG -> VaultPrimaryCyanLight
        PasswordStrength.VERY_STRONG -> VaultAccentEmerald
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Password Generator",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Generate high-entropy vault-grade passwords synced to family",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        // Password Display Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.currentPassword,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("generated_password_text")
                    )

                    IconButton(
                        onClick = {
                            vaultViewModel.generateNewPassword()
                        },
                        modifier = Modifier.testTag("refresh_password_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(state.currentPassword))
                            vaultViewModel.syncGeneratedPasswordToHistory(state.currentPassword)
                            Toast.makeText(context, "Copied & Synced to Family History!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_generated_password")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Strength bar
                LinearProgressIndicator(
                    progress = { state.strength.score },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = strengthColor,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.strength.label,
                        color = strengthColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${state.entropy.toInt()} bits entropy",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breach Monitoring (k-Anonymity Have I Been Pwned API)
                val breachResult by vaultViewModel.generatorBreachResult.collectAsState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (breachResult?.isPwned == true) VaultDangerRose
                                           else if (breachResult?.checked == true) VaultAccentEmerald
                                           else VaultPrimaryCyanLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "HIBP Breach Check (k-Anonymity)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    breachResult == null -> "Only sends 5-char SHA1 prefix — full password is never exposed"
                                    breachResult!!.isPwned -> "Compromised! Appeared in ${breachResult!!.breachCount} public data leaks"
                                    breachResult!!.checked -> "Clean! 0 breaches found in public leak archives"
                                    else -> breachResult!!.errorMessage ?: "Unable to check"
                                },
                                fontSize = 11.sp,
                                color = when {
                                    breachResult?.isPwned == true -> VaultDangerRose
                                    breachResult?.checked == true -> VaultAccentEmerald
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        if (breachResult == null) {
                            TextButton(
                                onClick = { vaultViewModel.checkGeneratorPasswordBreach() },
                                modifier = Modifier.testTag("check_breach_btn")
                            ) {
                                Text("Check", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    vaultViewModel.syncGeneratedPasswordToHistory(state.currentPassword)
                    onSaveToVault(state.currentPassword)
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_to_vault_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Save to Vault",
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalButton(
                onClick = {
                    vaultViewModel.syncGeneratedPasswordToHistory(state.currentPassword)
                    Toast.makeText(context, "Synced to Family History!", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("sync_to_history_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sync History",
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Configuration Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Length: ${config.length} characters",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Slider(
                    value = config.length.toFloat(),
                    onValueChange = {
                        vaultViewModel.updateGeneratorConfig(config.copy(length = it.toInt()))
                    },
                    valueRange = 8f..64f,
                    steps = 55,
                    modifier = Modifier.testTag("password_length_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                GeneratorToggle(
                    label = "Uppercase (A-Z)",
                    checked = config.includeUppercase,
                    onCheckedChange = {
                        vaultViewModel.updateGeneratorConfig(config.copy(includeUppercase = it))
                    }
                )

                GeneratorToggle(
                    label = "Lowercase (a-z)",
                    checked = config.includeLowercase,
                    onCheckedChange = {
                        vaultViewModel.updateGeneratorConfig(config.copy(includeLowercase = it))
                    }
                )

                GeneratorToggle(
                    label = "Numbers (0-9)",
                    checked = config.includeNumbers,
                    onCheckedChange = {
                        vaultViewModel.updateGeneratorConfig(config.copy(includeNumbers = it))
                    }
                )

                GeneratorToggle(
                    label = "Symbols (!@#$)",
                    checked = config.includeSymbols,
                    onCheckedChange = {
                        vaultViewModel.updateGeneratorConfig(config.copy(includeSymbols = it))
                    }
                )

                GeneratorToggle(
                    label = "Avoid Ambiguous (1, l, I, 0, O)",
                    checked = config.avoidAmbiguous,
                    onCheckedChange = {
                        vaultViewModel.updateGeneratorConfig(config.copy(avoidAmbiguous = it))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun GeneratorToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

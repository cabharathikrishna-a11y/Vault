package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DecryptedVaultItem
import com.example.ui.screens.dialogs.AddEditItemSheet
import com.example.ui.screens.dialogs.ItemDetailSheet
import com.example.ui.screens.dialogs.UpdateDialog
import com.example.ui.screens.tabs.GeneratorTab
import com.example.ui.screens.tabs.HistoryTab
import com.example.ui.screens.tabs.SettingsTab
import com.example.ui.screens.tabs.VaultListTab
import com.example.ui.theme.VaultAccentEmerald
import com.example.ui.theme.VaultAccentGold
import com.example.ui.theme.VaultPrimaryCyanLight
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.VaultViewModel

sealed class HomeTab(val index: Int, val title: String, val icon: ImageVector, val tag: String) {
    data object Vault : HomeTab(0, "Vault", Icons.Default.Shield, "nav_vault")
    data object Generator : HomeTab(1, "Generator", Icons.Default.AutoFixHigh, "nav_generator")
    data object History : HomeTab(2, "History", Icons.Default.History, "nav_history")
    data object Settings : HomeTab(3, "Settings", Icons.Default.Settings, "nav_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vaultViewModel: VaultViewModel,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val family by vaultViewModel.currentFamily.collectAsState()
    val statusMessage by vaultViewModel.statusMessage.collectAsState()
    val updateInfo by vaultViewModel.updateInfo.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<DecryptedVaultItem?>(null) }
    var itemToEdit by remember { mutableStateOf<DecryptedVaultItem?>(null) }
    var initialAddCategory by remember { mutableStateOf("Passwords") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            vaultViewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(VaultPrimaryCyanLight.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = VaultPrimaryCyanLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = family?.name ?: "Family Vault",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(VaultAccentEmerald)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "E2E Encrypted · Synced",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Quick Lock Button
                    IconButton(
                        onClick = {
                            vaultViewModel.lockApp()
                            Toast.makeText(context, "Vault locked", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("lock_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val tabs = listOf(HomeTab.Vault, HomeTab.Generator, HomeTab.History, HomeTab.Settings)
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == tab.index,
                        onClick = { selectedTabIndex = tab.index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp) },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = {
                        initialAddCategory = "Passwords"
                        itemToEdit = null
                        showAddSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_item_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> VaultListTab(
                    vaultViewModel = vaultViewModel,
                    onItemClick = { item -> selectedItemForDetail = item }
                )
                1 -> GeneratorTab(
                    vaultViewModel = vaultViewModel,
                    onSaveToVault = { pwd ->
                        initialAddCategory = "Passwords"
                        itemToEdit = null
                        showAddSheet = true
                    }
                )
                2 -> HistoryTab(
                    vaultViewModel = vaultViewModel
                )
                3 -> SettingsTab(
                    vaultViewModel = vaultViewModel,
                    authViewModel = authViewModel
                )
            }
        }
    }

    // Add or Edit Item Sheet
    if (showAddSheet) {
        AddEditItemSheet(
            vaultViewModel = vaultViewModel,
            existingItem = itemToEdit,
            initialCategory = initialAddCategory,
            onDismiss = {
                showAddSheet = false
                itemToEdit = null
            }
        )
    }

    // Detail Sheet
    if (selectedItemForDetail != null) {
        ItemDetailSheet(
            item = selectedItemForDetail!!,
            vaultViewModel = vaultViewModel,
            onEdit = {
                itemToEdit = selectedItemForDetail
                selectedItemForDetail = null
                showAddSheet = true
            },
            onDismiss = { selectedItemForDetail = null }
        )
    }

    // In-App APK Update Dialog
    val currentUpdate = updateInfo
    if (currentUpdate != null) {
        UpdateDialog(
            updateInfo = currentUpdate,
            onDismiss = { vaultViewModel.dismissUpdateDialog() }
        )
    }
}

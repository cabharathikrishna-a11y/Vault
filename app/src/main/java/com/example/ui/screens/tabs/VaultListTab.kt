package com.example.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DecryptedVaultItem
import com.example.ui.theme.VaultAccentEmerald
import com.example.ui.theme.VaultAccentGold
import com.example.ui.theme.VaultDangerRose
import com.example.ui.theme.VaultPrimaryCyanLight
import com.example.ui.viewmodel.VaultViewModel

@Composable
fun VaultListTab(
    vaultViewModel: VaultViewModel,
    onItemClick: (DecryptedVaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by vaultViewModel.decryptedItems.collectAsState()
    val searchQuery by vaultViewModel.searchQuery.collectAsState()
    val selectedCategory by vaultViewModel.selectedCategory.collectAsState()

    // Dashboard item counts
    val privateCount = items.count { it.isPrivate }
    val passwordsCount = items.count { it.itemType == "password" || it.payload.category == "Passwords" }
    val documentsCount = items.count { it.itemType == "document" || it.payload.category == "Documents" || it.payload.category == "Aadhaar Card" || it.payload.category == "ID Cards" }
    val othersCount = items.count { it.itemType == "other" || it.payload.category == "Others" || it.payload.customFields.isNotEmpty() }

    val entireFamilyCount = items.count { it.payload.belongsTo == "Entire Family" || it.payload.belongsTo.isBlank() }
    val member1Count = items.count { it.payload.belongsTo == "Bharathikrishna Muneeswaran" }
    val member2Count = items.count { it.payload.belongsTo == "Muneeswaran Palanisamy" }
    val member3Count = items.count { it.payload.belongsTo == "Deepa Muneeswaran" }

    val filteredItems = items.filter { item ->
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Private" -> item.isPrivate
            "Passwords" -> item.itemType == "password" || item.payload.category == "Passwords"
            "Documents" -> item.itemType == "document" || item.payload.category == "Documents" || item.payload.category == "Aadhaar Card" || item.payload.category == "ID Cards"
            "Others" -> item.itemType == "other" || item.payload.category == "Others" || item.payload.customFields.isNotEmpty()
            "Entire Family" -> item.payload.belongsTo == "Entire Family" || item.payload.belongsTo.isBlank()
            "Bharathikrishna Muneeswaran" -> item.payload.belongsTo == "Bharathikrishna Muneeswaran"
            "Muneeswaran Palanisamy" -> item.payload.belongsTo == "Muneeswaran Palanisamy"
            "Deepa Muneeswaran" -> item.payload.belongsTo == "Deepa Muneeswaran"
            else -> item.payload.belongsTo == selectedCategory || item.payload.category == selectedCategory
        }

        val matchesSearch = if (searchQuery.isBlank()) true else {
            item.title.contains(searchQuery, ignoreCase = true) ||
            item.payload.username.contains(searchQuery, ignoreCase = true) ||
            item.payload.docNumber.contains(searchQuery, ignoreCase = true) ||
            item.payload.notes.contains(searchQuery, ignoreCase = true) ||
            item.payload.belongsTo.contains(searchQuery, ignoreCase = true)
        }

        matchesCategory && matchesSearch
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search Bar (Strictly 1 line)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { vaultViewModel.setSearchQuery(it.replace("\n", "").replace("\r", "")) },
            placeholder = { Text("Search passwords, documents, members...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search Icon")
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { vaultViewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            } else null,
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("search_vault_input")
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Dashboard Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "Vault Dashboard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedCategory != "All") {
                        TextButton(
                            onClick = { vaultViewModel.setSelectedCategory("All") },
                            modifier = Modifier.testTag("show_all_button")
                        ) {
                            Text("Reset Filter", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Categories Grid (2x2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DashboardBoxCard(
                            title = "Private Things",
                            subtitle = "$privateCount items",
                            count = privateCount,
                            icon = Icons.Default.Lock,
                            iconTint = VaultDangerRose,
                            isSelected = selectedCategory == "Private",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Private") "All" else "Private")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DashboardBoxCard(
                            title = "Passwords",
                            subtitle = "$passwordsCount items",
                            count = passwordsCount,
                            icon = Icons.Default.Key,
                            iconTint = VaultAccentEmerald,
                            isSelected = selectedCategory == "Passwords",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Passwords") "All" else "Passwords")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DashboardBoxCard(
                            title = "Documents",
                            subtitle = "$documentsCount items",
                            count = documentsCount,
                            icon = Icons.Default.Description,
                            iconTint = VaultPrimaryCyanLight,
                            isSelected = selectedCategory == "Documents",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Documents") "All" else "Documents")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DashboardBoxCard(
                            title = "Others",
                            subtitle = "$othersCount items",
                            count = othersCount,
                            icon = Icons.Default.Folder,
                            iconTint = VaultAccentGold,
                            isSelected = selectedCategory == "Others",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Others") "All" else "Others")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Family Members Grid Section Title
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Family Members",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Family Members Boxes (2x2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DashboardBoxCard(
                            title = "Entire Family",
                            subtitle = "$entireFamilyCount shared",
                            count = entireFamilyCount,
                            icon = Icons.Default.Group,
                            iconTint = MaterialTheme.colorScheme.primary,
                            isSelected = selectedCategory == "Entire Family",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Entire Family") "All" else "Entire Family")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DashboardBoxCard(
                            title = "Bharathikrishna M.",
                            subtitle = "$member1Count items",
                            count = member1Count,
                            icon = Icons.Default.Person,
                            iconTint = VaultAccentEmerald,
                            isSelected = selectedCategory == "Bharathikrishna Muneeswaran",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Bharathikrishna Muneeswaran") "All" else "Bharathikrishna Muneeswaran")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DashboardBoxCard(
                            title = "Muneeswaran P.",
                            subtitle = "$member2Count items",
                            count = member2Count,
                            icon = Icons.Default.Person,
                            iconTint = VaultPrimaryCyanLight,
                            isSelected = selectedCategory == "Muneeswaran Palanisamy",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Muneeswaran Palanisamy") "All" else "Muneeswaran Palanisamy")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DashboardBoxCard(
                            title = "Deepa M.",
                            subtitle = "$member3Count items",
                            count = member3Count,
                            icon = Icons.Default.Person,
                            iconTint = VaultAccentGold,
                            isSelected = selectedCategory == "Deepa Muneeswaran",
                            onClick = {
                                vaultViewModel.setSelectedCategory(if (selectedCategory == "Deepa Muneeswaran") "All" else "Deepa Muneeswaran")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Active Filter Chip / Status
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedCategory == "All") "Showing All Vault Items (${filteredItems.size})"
                                   else "Filtered: $selectedCategory (${filteredItems.size} items)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (selectedCategory != "All") {
                        IconButton(
                            onClick = { vaultViewModel.setSelectedCategory("All") },
                            modifier = Modifier.size(28.dp).testTag("clear_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Items List
            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Empty Category",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching items found"
                                       else if (selectedCategory != "All") "No items under $selectedCategory"
                                       else "Your Family Vault is empty",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap + at bottom to add items to this category or member",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    VaultItemCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardBoxCard(
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("dashboard_box_${title.replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else iconTint.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VaultItemCard(
    item: DecryptedVaultItem,
    onClick: () -> Unit
) {
    val icon: ImageVector = when {
        item.payload.category == "Aadhaar Card" || item.title.contains("Aadhaar", ignoreCase = true) -> Icons.Default.Badge
        item.itemType == "id_card" -> Icons.Default.CreditCard
        item.hasAttachment || item.itemType == "document" -> Icons.Default.Description
        item.itemType == "other" || item.payload.category == "Others" -> Icons.Default.Folder
        else -> Icons.Default.Key
    }

    val iconTint = when {
        item.payload.category == "Aadhaar Card" -> VaultAccentGold
        item.isPrivate -> VaultDangerRose
        item.hasAttachment -> VaultPrimaryCyanLight
        else -> VaultAccentEmerald
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vault_item_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (item.isPrivate) {
                        Text(
                            text = "Private",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultDangerRose
                        )
                    } else {
                        Text(
                            text = if (item.payload.belongsTo.isNotBlank() && item.payload.belongsTo != "Entire Family")
                                item.payload.belongsTo.split(" ").firstOrNull() ?: item.payload.belongsTo
                            else "Family Shared",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VaultAccentEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val subText = when {
                    item.payload.category == "Aadhaar Card" && item.payload.docNumber.isNotBlank() ->
                        "Aadhaar: ${item.payload.docNumber}"
                    item.payload.username.isNotBlank() ->
                        item.payload.username
                    item.payload.docNumber.isNotBlank() ->
                        item.payload.docNumber
                    item.payload.fileName.isNotBlank() ->
                        item.payload.fileName
                    else ->
                        item.payload.category
                }

                Text(
                    text = subText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

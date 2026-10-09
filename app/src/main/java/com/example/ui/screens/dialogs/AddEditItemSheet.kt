package com.example.ui.screens.dialogs

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AllowedFamilyMembers
import com.example.data.model.DecryptedVaultItem
import com.example.data.model.VaultPayload
import com.example.generator.PasswordGenerator
import com.example.generator.PasswordGeneratorConfig
import com.example.ui.theme.VaultAccentEmerald
import com.example.ui.theme.VaultAccentGold
import com.example.ui.theme.VaultDangerRose
import com.example.ui.theme.VaultPrimaryCyanLight
import com.example.ui.viewmodel.VaultViewModel
import java.io.InputStream
import java.util.UUID

data class EditableCustomField(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var type: String = "Text", // "Text", "Password", "Number", "Secret", "Date"
    var value: String = "",
    var isVisible: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemSheet(
    vaultViewModel: VaultViewModel,
    existingItem: DecryptedVaultItem? = null,
    initialCategory: String = "Passwords",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Categories: Passwords, Documents, Others
    val validCategories = listOf("Passwords", "Documents", "Others")
    var selectedCategory by remember {
        mutableStateOf(
            if (existingItem?.payload?.category in validCategories) {
                existingItem!!.payload.category
            } else if (initialCategory in validCategories) {
                initialCategory
            } else {
                "Passwords"
            }
        )
    }

    var itemType by remember {
        mutableStateOf(
            when (selectedCategory) {
                "Passwords" -> "password"
                "Documents" -> "document"
                else -> "other"
            }
        )
    }

    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var belongsTo by remember { mutableStateOf(existingItem?.payload?.belongsTo ?: "Entire Family") }
    var expandedBelongsTo by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(existingItem?.isPrivate ?: false) }

    // Password fields
    var username by remember { mutableStateOf(existingItem?.payload?.username ?: "") }
    var password by remember { mutableStateOf(existingItem?.payload?.password ?: "") }
    var website by remember { mutableStateOf(existingItem?.payload?.website ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Document / Attachment fields
    var docNumber by remember { mutableStateOf(existingItem?.payload?.docNumber ?: "") }
    var holderName by remember { mutableStateOf(existingItem?.payload?.holderName ?: "") }

    var attachmentBase64 by remember { mutableStateOf(existingItem?.payload?.attachmentBase64 ?: "") }
    var fileName by remember { mutableStateOf(existingItem?.payload?.fileName ?: "") }
    var mimeType by remember { mutableStateOf(existingItem?.payload?.mimeType ?: "") }
    var pdfPassword by remember { mutableStateOf(existingItem?.payload?.pdfPassword ?: "") }

    var notes by remember { mutableStateOf(existingItem?.payload?.notes ?: "") }

    // Custom fields for "Others" category
    val customFieldsList = remember {
        mutableStateListOf<EditableCustomField>().apply {
            if (existingItem != null && existingItem.payload.customFields.isNotEmpty()) {
                existingItem.payload.customFields.forEach { (k, v) ->
                    val type = existingItem.payload.customFieldTypes[k] ?: "Text"
                    add(EditableCustomField(name = k, type = type, value = v))
                }
            }
        }
    }

    // PDF Password dialog state
    var showPdfPasswordDialog by remember { mutableStateOf(false) }
    var pendingPdfPassword by remember { mutableStateOf("") }
    var pdfPassVisible by remember { mutableStateOf(false) }

    // File picker launcher (attaches anything: PDF, JPG, PNG, DOC, etc.)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val detectedMime = context.contentResolver.getType(it) ?: ""
            val extractedName = queryFileName(context, it) ?: "Document_${System.currentTimeMillis()}"
            val base64 = readLosslessBase64FromUri(context, it)
            if (base64 != null) {
                attachmentBase64 = base64
                fileName = extractedName
                mimeType = detectedMime

                val isProtected = isPdfPasswordProtected(context, it)
                if (isProtected) {
                    pendingPdfPassword = ""
                    showPdfPasswordDialog = true
                } else {
                    Toast.makeText(context, "Attachment added successfully", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Could not read attached file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (existingItem == null) "Add Vault Item" else "Edit Vault Item",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selector - Passwords, Documents, Others
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(validCategories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = cat
                            itemType = when (cat) {
                                "Passwords" -> "password"
                                "Documents" -> "document"
                                else -> "other"
                            }
                            if (cat == "Others" && customFieldsList.isEmpty()) {
                                customFieldsList.add(EditableCustomField(name = "Field 1", type = "Text", value = ""))
                            }
                        },
                        label = { Text(cat, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title / Label") },
                placeholder = {
                    Text(
                        when (selectedCategory) {
                            "Passwords" -> "e.g. Google, Netflix, Wi-Fi"
                            "Documents" -> "e.g. Passport, Aadhaar, Contract"
                            else -> "e.g. License Key, Secret Note, Config"
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("item_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Belongs To Dropdown Selector
            Text("Belongs To / Scope", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = belongsTo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Family or Particular Member") },
                    trailingIcon = {
                        IconButton(onClick = { expandedBelongsTo = !expandedBelongsTo }) {
                            Icon(
                                imageVector = if (expandedBelongsTo) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Select Member"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedBelongsTo = true }
                )
                DropdownMenu(
                    expanded = expandedBelongsTo,
                    onDismissRequest = { expandedBelongsTo = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    AllowedFamilyMembers.DROPDOWN_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontWeight = if (option == belongsTo) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == belongsTo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                belongsTo = option
                                expandedBelongsTo = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Specific Form
            when (selectedCategory) {
                "Passwords" -> {
                    // Passwords Fields
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email / Mobile") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_username_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    password = PasswordGenerator.generate(PasswordGeneratorConfig(length = 18))
                                    passwordVisible = true
                                }) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Generate", tint = VaultPrimaryCyanLight)
                                }
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_password_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text("Website / App URL") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                "Documents" -> {
                    // Documents Category
                    OutlinedTextField(
                        value = docNumber,
                        onValueChange = { docNumber = it },
                        label = { Text("Document / Reference Number (Optional)") },
                        placeholder = { Text("e.g. Passport No, License No") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = holderName,
                        onValueChange = { holderName = it },
                        label = { Text("Holder / Owner Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Attachment section for Documents
                    AttachmentSection(
                        attachmentBase64 = attachmentBase64,
                        fileName = fileName,
                        mimeType = mimeType,
                        pdfPassword = pdfPassword,
                        onPdfPasswordChange = { pdfPassword = it },
                        onPickFile = { filePickerLauncher.launch("*/*") },
                        onRemoveAttachment = {
                            attachmentBase64 = ""
                            fileName = ""
                            mimeType = ""
                            pdfPassword = ""
                        }
                    )
                }

                "Others" -> {
                    // Custom Fields for "Others" category
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Custom Fields",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = {
                            customFieldsList.add(
                                EditableCustomField(
                                    name = "Field ${customFieldsList.size + 1}",
                                    type = "Text",
                                    value = ""
                                )
                            )
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Field", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (customFieldsList.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No custom fields added yet. Tap '+ Add Field' above to define key-value pairs (e.g. Serial Key, PIN, Expiry).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        customFieldsList.forEachIndexed { index, field ->
                            CustomFieldEditCard(
                                field = field,
                                onUpdate = { updated ->
                                    customFieldsList[index] = updated
                                },
                                onDelete = {
                                    customFieldsList.removeAt(index)
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Optional Attachment Section for Others
                    Text(
                        text = "Attach File (Optional)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Optionally attach a document, image, PDF or file. Not mandatory.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AttachmentSection(
                        attachmentBase64 = attachmentBase64,
                        fileName = fileName,
                        mimeType = mimeType,
                        pdfPassword = pdfPassword,
                        onPdfPasswordChange = { pdfPassword = it },
                        onPickFile = { filePickerLauncher.launch("*/*") },
                        onRemoveAttachment = {
                            attachmentBase64 = ""
                            fileName = ""
                            mimeType = ""
                            pdfPassword = ""
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Secure Notes") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Private vs Shared with Family Switch
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPrivate) "Private Item (Personal Only)" else "Shared with Family",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (isPrivate) VaultDangerRose else VaultAccentEmerald
                        )
                        Text(
                            text = if (isPrivate) "Only visible on your account" else "All family members can access and decrypt",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        modifier = Modifier.testTag("private_item_toggle")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val customFieldsMap = mutableMapOf<String, String>()
                    val customFieldTypesMap = mutableMapOf<String, String>()

                    if (selectedCategory == "Others") {
                        customFieldsList.filter { it.name.isNotBlank() }.forEach { cf ->
                            val key = cf.name.trim()
                            customFieldsMap[key] = cf.value
                            customFieldTypesMap[key] = cf.type
                        }
                    }

                    val payload = VaultPayload(
                        username = username,
                        password = password,
                        website = website,
                        notes = notes,
                        category = selectedCategory,
                        docNumber = docNumber,
                        holderName = holderName,
                        fileName = fileName,
                        mimeType = mimeType,
                        attachmentBase64 = attachmentBase64,
                        pdfPassword = pdfPassword,
                        belongsTo = belongsTo,
                        customFields = customFieldsMap,
                        customFieldTypes = customFieldTypesMap
                    )

                    vaultViewModel.saveVaultItem(
                        id = existingItem?.id ?: "",
                        title = title,
                        payload = payload,
                        itemType = itemType,
                        isPrivate = isPrivate
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_vault_item_button")
            ) {
                Text("Encrypt & Save to Vault", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }

    // PDF Password Dialog
    if (showPdfPasswordDialog) {
        var dialogPassVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPdfPasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = VaultAccentGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PDF is Password Protected", fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        "This attached PDF document is encrypted or password-protected. Enter the PDF password so family members can unlock and view it.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pendingPdfPassword,
                        onValueChange = { pendingPdfPassword = it },
                        label = { Text("PDF Password") },
                        singleLine = true,
                        visualTransformation = if (dialogPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { dialogPassVisible = !dialogPassVisible }) {
                                Icon(
                                    if (dialogPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    pdfPassword = pendingPdfPassword
                    showPdfPasswordDialog = false
                    Toast.makeText(context, "PDF password set!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPdfPasswordDialog = false }) {
                    Text("Skip")
                }
            }
        )
    }
}

@Composable
private fun CustomFieldEditCard(
    field: EditableCustomField,
    onUpdate: (EditableCustomField) -> Unit,
    onDelete: () -> Unit
) {
    val types = listOf("Text", "Password", "Secret", "Number", "Date")
    var isPassVis by remember { mutableStateOf(field.isVisible) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = field.name,
                    onValueChange = { onUpdate(field.copy(name = it)) },
                    label = { Text("Field Name") },
                    placeholder = { Text("e.g. License Key, PIN") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Field", tint = VaultDangerRose)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Field Type FilterChips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(types) { t ->
                    FilterChip(
                        selected = field.type == t,
                        onClick = { onUpdate(field.copy(type = t)) },
                        label = { Text(t, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Field Value TextField
            val isSecretType = field.type in listOf("Password", "Secret")
            OutlinedTextField(
                value = field.value,
                onValueChange = { onUpdate(field.copy(value = it)) },
                label = { Text("${field.name.ifBlank { "Field" }} Value") },
                singleLine = true,
                visualTransformation = if (isSecretType && !isPassVis) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSecretType) {
                            IconButton(onClick = {
                                val generated = PasswordGenerator.generate(PasswordGeneratorConfig(length = 16))
                                onUpdate(field.copy(value = generated))
                                isPassVis = true
                            }) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = "Generate", tint = VaultPrimaryCyanLight)
                            }
                            IconButton(onClick = { isPassVis = !isPassVis }) {
                                Icon(
                                    if (isPassVis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AttachmentSection(
    attachmentBase64: String,
    fileName: String,
    mimeType: String,
    pdfPassword: String,
    onPdfPasswordChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: () -> Unit
) {
    if (attachmentBase64.isBlank()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onPickFile() }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = "Attach File",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Attach PDF, JPG or File (Optional)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap to pick file from device",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)) {
                                Icons.Default.Description
                            } else {
                                Icons.Default.AttachFile
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = fileName.ifBlank { "Attached Document" },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (mimeType.isNotBlank()) mimeType else "Document Attachment",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onRemoveAttachment) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                    }
                }

                var pdfPassVis by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pdfPassword,
                    onValueChange = onPdfPasswordChange,
                    label = { Text("PDF Password (if protected)") },
                    placeholder = { Text("Enter password to decrypt PDF") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { pdfPassVis = !pdfPassVis }) {
                            Icon(
                                if (pdfPassVis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (pdfPassVis) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        } catch (_: Exception) {}
    }
    if (name == null) {
        name = uri.path
        val cut = name?.lastIndexOf('/') ?: -1
        if (cut != -1) name = name?.substring(cut + 1)
    }
    return name
}

private fun isPdfPasswordProtected(context: Context, uri: Uri): Boolean {
    val mimeType = context.contentResolver.getType(uri) ?: ""
    val isPdf = mimeType.contains("pdf", ignoreCase = true) || uri.path?.lowercase()?.endsWith(".pdf") == true
    if (!isPdf) return false

    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            try {
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                renderer.close()
                false
            } catch (e: SecurityException) {
                true
            } catch (_: Exception) {
                checkPdfBytesForEncryption(context, uri)
            }
        } ?: checkPdfBytesForEncryption(context, uri)
    } catch (_: Exception) {
        checkPdfBytesForEncryption(context, uri)
    }
}

private fun checkPdfBytesForEncryption(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            val contentString = String(bytes, Charsets.ISO_8859_1)
            contentString.contains("/Encrypt")
        } ?: false
    } catch (_: Exception) {
        false
    }
}

private fun readLosslessBase64FromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
            val bytes = inputStream.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        null
    }
}

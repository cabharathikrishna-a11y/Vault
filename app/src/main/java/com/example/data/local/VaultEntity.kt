package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_vault_items")
data class LocalVaultItemEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val isPrivate: Boolean,
    val itemType: String,
    val encryptedTitle: String,
    val encryptedPayload: String,
    val createdByUid: String,
    val createdByName: String,
    val memberUidsJson: String,
    val hasAttachment: Boolean,
    val attachmentType: String,
    val updatedAtMs: Long,
    val isSynced: Boolean = true
)

@Entity(tableName = "local_family")
data class LocalFamilyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdBy: String,
    val memberUidsJson: String,
    val salt: String,
    val keyCheckHash: String,
    val updatedAtMs: Long
)

@Entity(tableName = "local_password_history")
data class LocalPasswordHistoryEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val encryptedPassword: String,
    val length: Int,
    val optionsSummary: String,
    val generatedByUid: String,
    val generatedByName: String,
    val memberUidsJson: String,
    val createdAtMs: Long
)

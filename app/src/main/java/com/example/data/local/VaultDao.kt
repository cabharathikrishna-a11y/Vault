package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM local_vault_items WHERE familyId = :familyId AND isPrivate = 0 ORDER BY updatedAtMs DESC")
    fun getFamilyItems(familyId: String): Flow<List<LocalVaultItemEntity>>

    @Query("SELECT * FROM local_vault_items WHERE familyId = :familyId AND isPrivate = 0 ORDER BY updatedAtMs DESC")
    suspend fun getFamilyItemsOnce(familyId: String): List<LocalVaultItemEntity>

    @Query("SELECT * FROM local_vault_items WHERE createdByUid = :userId AND isPrivate = 1 ORDER BY updatedAtMs DESC")
    fun getPrivateItems(userId: String): Flow<List<LocalVaultItemEntity>>

    @Query("SELECT * FROM local_vault_items WHERE createdByUid = :userId AND isPrivate = 1 ORDER BY updatedAtMs DESC")
    suspend fun getPrivateItemsOnce(userId: String): List<LocalVaultItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<LocalVaultItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: LocalVaultItemEntity)

    @Query("DELETE FROM local_vault_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("SELECT * FROM local_password_history WHERE familyId = :familyId ORDER BY createdAtMs DESC")
    fun getPasswordHistory(familyId: String): Flow<List<LocalPasswordHistoryEntity>>

    @Query("SELECT * FROM local_password_history WHERE familyId = :familyId ORDER BY createdAtMs DESC")
    suspend fun getPasswordHistoryOnce(familyId: String): List<LocalPasswordHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasswordHistory(history: List<LocalPasswordHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasswordHistoryItem(item: LocalPasswordHistoryEntity)

    @Query("SELECT * FROM local_family WHERE id = :familyId LIMIT 1")
    fun getFamily(familyId: String): Flow<LocalFamilyEntity?>

    @Query("SELECT * FROM local_family WHERE id = :familyId LIMIT 1")
    suspend fun getFamilyOnce(familyId: String): LocalFamilyEntity?

    @Query("SELECT * FROM local_family LIMIT 1")
    suspend fun getAnyFamilyOnce(): LocalFamilyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamily(family: LocalFamilyEntity)
}

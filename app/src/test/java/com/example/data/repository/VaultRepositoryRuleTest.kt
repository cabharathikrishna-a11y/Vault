package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FamilyVault
import com.example.data.model.VaultItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun testCreateFamilyAndSaveVaultItem() = runBlocking {
        val userEmail = "alice@example.com"
        val uid = signInTestUser(userEmail)

        val repository = FirestoreVaultRepository(firestore, null)

        val createResult = repository.createFamily("Smith Family Vault", "test_salt_123", "test_hash_456")
        assertTrue("Create family should succeed", createResult.isSuccess)
        val family = createResult.getOrThrow()
        assertEquals("Smith Family Vault", family.name)
        assertTrue("Creator is member", family.memberUids.contains(uid))

        // Save a vault item
        val item = VaultItem(
            id = "item_pwd_1",
            familyId = family.id,
            isPrivate = false,
            itemType = "password",
            encryptedTitle = "enc_gmail",
            encryptedPayload = "enc_secret_payload",
            createdByUid = uid,
            createdByName = "Alice",
            memberUids = family.memberUids
        )

        val saveResult = repository.saveVaultItem(item)
        assertTrue("Save vault item should succeed", saveResult.isSuccess)
    }

    @Test
    fun testPrivateVaultItemAccess() = runBlocking {
        val userEmail = "bob@example.com"
        val uid = signInTestUser(userEmail)

        val repository = FirestoreVaultRepository(firestore, null)

        val privateItem = VaultItem(
            id = "priv_item_1",
            familyId = "fam_standalone",
            isPrivate = true,
            itemType = "note",
            encryptedTitle = "enc_private_note",
            encryptedPayload = "enc_secret_note",
            createdByUid = uid,
            createdByName = "Bob",
            memberUids = listOf(uid)
        )

        val saveResult = repository.saveVaultItem(privateItem)
        assertTrue("Save private item should succeed", saveResult.isSuccess)
    }
}

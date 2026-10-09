package com.example.data.model

import com.google.firebase.Timestamp
import org.json.JSONObject

object AllowedFamilyMembers {
    val ALLOWED_EMAILS = setOf(
        "cabharathikrishna@gmail.com",
        "muneeswaran45@gmail.com",
        "muneeswarandeepa@gmail.com"
    )

    val MEMBER_NAMES = mapOf(
        "cabharathikrishna@gmail.com" to "Bharathikrishna Muneeswaran",
        "muneeswaran45@gmail.com" to "Muneeswaran Palanisamy",
        "muneeswarandeepa@gmail.com" to "Deepa Muneeswaran"
    )

    val DROPDOWN_OPTIONS = listOf(
        "Entire Family",
        "Bharathikrishna Muneeswaran",
        "Muneeswaran Palanisamy",
        "Deepa Muneeswaran"
    )

    fun isAllowed(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return ALLOWED_EMAILS.contains(email.trim().lowercase())
    }

    fun getDisplayName(email: String?): String {
        if (email.isNullOrBlank()) return "Family Member"
        return MEMBER_NAMES[email.trim().lowercase()] ?: email
    }
}

// Firestore Data Classes - MUST provide default values for every property
data class FamilyVault(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val memberUids: List<String> = emptyList(),
    val salt: String = "",
    val keyCheckHash: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class VaultItem(
    val id: String = "",
    val familyId: String = "",
    val isPrivate: Boolean = false,
    val itemType: String = "password", // "password", "document", "id_card", "note", "bank"
    val encryptedTitle: String = "",
    val encryptedPayload: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val memberUids: List<String> = emptyList(),
    val hasAttachment: Boolean = false,
    val attachmentType: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class PasswordHistory(
    val id: String = "",
    val familyId: String = "",
    val encryptedPassword: String = "",
    val length: Int = 16,
    val optionsSummary: String = "",
    val generatedByUid: String = "",
    val generatedByName: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val familyId: String = "",
    val joinedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

// Decrypted Domain Models
data class DecryptedVaultItem(
    val id: String,
    val familyId: String,
    val isPrivate: Boolean,
    val itemType: String,
    val title: String,
    val payload: VaultPayload,
    val createdByUid: String,
    val createdByName: String,
    val memberUids: List<String>,
    val hasAttachment: Boolean,
    val attachmentType: String,
    val updatedAt: Timestamp? = null
)

data class VaultPayload(
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
    val category: String = "Passwords", // Passwords, Aadhaar Card, ID Cards, Documents, Bank & Finance
    val docNumber: String = "",
    val holderName: String = "",
    val dob: String = "",
    val address: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val attachmentBase64: String = "",
    val attachmentFrontBase64: String = "", // For Aadhaar / ID front
    val attachmentBackBase64: String = "",  // For Aadhaar / ID back
    val pdfPassword: String = "",           // Password for encrypted PDF attachments
    val isPasskey: Boolean = false,         // Passkey (FIDO2 WebAuthn token)
    val rpId: String = "",                  // Relying Party ID (e.g. google.com, github.com)
    val userHandle: String = "",            // Passkey user handle
    val credentialId: String = "",          // FIDO2 Credential ID
    val publicKey: String = "",             // Public key representation
    val passkeyCreatedAt: String = "",
    val belongsTo: String = "Entire Family", // "Entire Family", "Bharathikrishna Muneeswaran", "Muneeswaran Palanisamy", "Deepa Muneeswaran"
    val customFields: Map<String, String> = emptyMap(),
    val customFieldTypes: Map<String, String> = emptyMap()
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("username", username)
        json.put("password", password)
        json.put("website", website)
        json.put("notes", notes)
        json.put("category", category)
        json.put("docNumber", docNumber)
        json.put("holderName", holderName)
        json.put("dob", dob)
        json.put("address", address)
        json.put("fileName", fileName)
        json.put("mimeType", mimeType)
        json.put("attachmentBase64", attachmentBase64)
        json.put("attachmentFrontBase64", attachmentFrontBase64)
        json.put("attachmentBackBase64", attachmentBackBase64)
        json.put("pdfPassword", pdfPassword)
        json.put("isPasskey", isPasskey)
        json.put("rpId", rpId)
        json.put("userHandle", userHandle)
        json.put("credentialId", credentialId)
        json.put("publicKey", publicKey)
        json.put("passkeyCreatedAt", passkeyCreatedAt)
        json.put("belongsTo", belongsTo)
        
        val customObj = JSONObject()
        customFields.forEach { (k, v) -> customObj.put(k, v) }
        json.put("customFields", customObj)

        val customTypesObj = JSONObject()
        customFieldTypes.forEach { (k, v) -> customTypesObj.put(k, v) }
        json.put("customFieldTypes", customTypesObj)
        return json.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): VaultPayload {
            return try {
                val json = JSONObject(jsonStr)
                val customMap = mutableMapOf<String, String>()
                if (json.has("customFields")) {
                    val customObj = json.optJSONObject("customFields")
                    customObj?.keys()?.forEach { key ->
                        customMap[key] = customObj.optString(key)
                    }
                }
                val customTypesMap = mutableMapOf<String, String>()
                if (json.has("customFieldTypes")) {
                    val customTypesObj = json.optJSONObject("customFieldTypes")
                    customTypesObj?.keys()?.forEach { key ->
                        customTypesMap[key] = customTypesObj.optString(key)
                    }
                }
                VaultPayload(
                    username = json.optString("username"),
                    password = json.optString("password"),
                    website = json.optString("website"),
                    notes = json.optString("notes"),
                    category = json.optString("category", "Passwords"),
                    docNumber = json.optString("docNumber"),
                    holderName = json.optString("holderName"),
                    dob = json.optString("dob"),
                    address = json.optString("address"),
                    fileName = json.optString("fileName"),
                    mimeType = json.optString("mimeType"),
                    attachmentBase64 = json.optString("attachmentBase64"),
                    attachmentFrontBase64 = json.optString("attachmentFrontBase64"),
                    attachmentBackBase64 = json.optString("attachmentBackBase64"),
                    pdfPassword = json.optString("pdfPassword"),
                    isPasskey = json.optBoolean("isPasskey", false),
                    rpId = json.optString("rpId"),
                    userHandle = json.optString("userHandle"),
                    credentialId = json.optString("credentialId"),
                    publicKey = json.optString("publicKey"),
                    passkeyCreatedAt = json.optString("passkeyCreatedAt"),
                    belongsTo = json.optString("belongsTo", "Entire Family"),
                    customFields = customMap,
                    customFieldTypes = customTypesMap
                )
            } catch (e: Exception) {
                VaultPayload(notes = "Decryption error or corrupted payload: ${e.message}")
            }
        }
    }
}

data class DecryptedPasswordHistory(
    val id: String,
    val familyId: String,
    val password: String,
    val length: Int,
    val optionsSummary: String,
    val generatedByUid: String,
    val generatedByName: String,
    val createdAt: Timestamp? = null
)

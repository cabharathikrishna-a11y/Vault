package com.example.service

import android.app.PendingIntent
import android.app.slice.Slice
import android.app.slice.SliceSpec
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.BeginCreateCredentialRequest
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.ClearCredentialStateRequest
import android.service.credentials.CreateEntry
import android.service.credentials.CredentialEntry
import android.service.credentials.CredentialProviderService
import androidx.annotation.RequiresApi
import com.example.ui.screens.CredentialEntryActivity

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class FamilyVaultCredentialProviderService : CredentialProviderService() {

    override fun onBeginGetCredential(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, android.credentials.GetCredentialException>
    ) {
        val callingApp = request.callingAppInfo?.packageName ?: "Unknown"
        val responseBuilder = BeginGetCredentialResponse.Builder()

        request.beginGetCredentialOptions.forEach { option ->
            val fillIntent = Intent(this, CredentialEntryActivity::class.java).apply {
                val isPasskey = option.type.contains("publickey", ignoreCase = true)
                putExtra(
                    CredentialEntryActivity.EXTRA_MODE,
                    if (isPasskey) CredentialEntryActivity.MODE_GET_PASSKEY else CredentialEntryActivity.MODE_GET_PASSWORD
                )
                putExtra(CredentialEntryActivity.EXTRA_CALLING_PACKAGE, callingApp)
                if (isPasskey) {
                    val rpId = option.candidateQueryData.getString("androidx.credentials.BUNDLE_KEY_REQUEST_JSON") ?: callingApp
                    putExtra(CredentialEntryActivity.EXTRA_RP_ID, rpId)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                option.id.hashCode(),
                fillIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val actionSlice = Slice.Builder(
                Uri.parse("content://com.example.familyvault.credential/action/${option.id}"),
                SliceSpec("Action", 1)
            ).build()

            val slice = Slice.Builder(
                Uri.parse("content://com.example.familyvault.credential/${option.id}"),
                SliceSpec("FamilyVaultCredential", 1)
            ).addText("Family Vault", null, listOf(Slice.HINT_TITLE))
             .addAction(pendingIntent, actionSlice, Slice.HINT_ACTIONS)
             .build()

            val entry = CredentialEntry(option.id, option.type, slice)
            responseBuilder.addCredentialEntry(entry)
        }

        callback.onResult(responseBuilder.build())
    }

    override fun onBeginCreateCredential(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, android.credentials.CreateCredentialException>
    ) {
        val callingApp = request.callingAppInfo?.packageName ?: "Unknown"

        val saveIntent = Intent(this, CredentialEntryActivity::class.java).apply {
            putExtra(CredentialEntryActivity.EXTRA_MODE, CredentialEntryActivity.MODE_SAVE_CREDENTIAL)
            putExtra(CredentialEntryActivity.EXTRA_CALLING_PACKAGE, callingApp)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            103,
            saveIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionSlice = Slice.Builder(
            Uri.parse("content://com.example.familyvault.credential/action/save"),
            SliceSpec("Action", 1)
        ).build()

        val slice = Slice.Builder(
            Uri.parse("content://com.example.familyvault.credential/save_credential"),
            SliceSpec("FamilyVaultSave", 1)
        ).addText("Save to Family Vault", null, listOf(Slice.HINT_TITLE))
         .addAction(pendingIntent, actionSlice, Slice.HINT_ACTIONS)
         .build()

        val createEntry = CreateEntry(slice)

        val response = BeginCreateCredentialResponse.Builder()
            .addCreateEntry(createEntry)
            .build()

        callback.onResult(response)
    }

    override fun onClearCredentialState(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, android.credentials.ClearCredentialStateException>
    ) {
        callback.onResult(null)
    }
}

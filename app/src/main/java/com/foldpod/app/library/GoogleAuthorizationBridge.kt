package com.foldpod.app.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/** Official Google authorization UI; FoldPod never persists Google credentials. */
class GoogleAuthorizationBridge(
    context: Context,
    private val state: GoogleAuthorizationState = GoogleAuthorizationState(),
) {
    private val client = Identity.getAuthorizationClient(context)
    private var closed = false

    fun authorize(
        onResolution: (IntentSenderRequest) -> Unit,
        onAuthorized: (String) -> Unit,
        onError: (String) -> Unit,
        automatic: Boolean = false,
    ) {
        if (closed) return
        if (state.pendingGeneration != null) {
            if (automatic) return
            onError("열려 있는 Google 권한 화면을 먼저 닫고 다시 시도해 주세요.")
            return
        }
        val requestGeneration = if (automatic) state.beginAutomaticRequest() ?: return else state.beginRequest()
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY)))
            .build()
        fun requestAuthorization() {
            if (closed || requestGeneration != state.generation) return
            client.authorize(request)
                .addOnSuccessListener { result ->
                    if (closed || requestGeneration != state.generation) return@addOnSuccessListener
                    if (result.hasResolution()) {
                        if (automatic) {
                            onError("Google 자동 연결을 완료하지 못했습니다. 연결 버튼으로 다시 인증해 주세요.")
                            return@addOnSuccessListener
                        }
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent == null) {
                            onError("Google 권한 요청을 시작하지 못했습니다. 다시 연결해 주세요.")
                        } else {
                            state.markPending(requestGeneration)
                            try {
                                onResolution(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                            } catch (_: Exception) {
                                state.consumePending()
                                onError("Google 권한 화면을 열지 못했습니다. 다시 연결해 주세요.")
                            }
                        }
                    } else {
                        deliver(result, requestGeneration, onAuthorized, onError)
                    }
                }
                .addOnFailureListener {
                    if (!closed && requestGeneration == state.generation) {
                        onError("Google 연결에 실패했습니다. 네트워크와 Google Play services를 확인하고 연결 버튼으로 다시 시도해 주세요.")
                    }
                }
        }
        // Expired/rejected tokens must not be returned again by the GIS cache.
        val previousToken = state.lastAccessToken
        state.rememberToken(null)
        if (previousToken == null) {
            requestAuthorization()
        } else {
            client.clearToken(ClearTokenRequest.builder().setToken(previousToken).build())
                .addOnCompleteListener { requestAuthorization() }
        }
    }

    fun handleResult(
        resultCode: Int,
        data: Intent?,
        onAuthorized: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (closed) return
        // Consumption durably retires the saved consent identity; deliver under its successor.
        val completionGeneration = state.consumePending() ?: return
        if (completionGeneration != state.generation) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            onError("Google 연결이 취소되었습니다. 연결 버튼으로 다시 시도할 수 있습니다.")
            return
        }
        try {
            deliver(client.getAuthorizationResultFromIntent(data), completionGeneration, onAuthorized, onError)
        } catch (_: Exception) {
            onError("Google 권한 결과를 확인하지 못했습니다. 다시 연결해 주세요.")
        }
    }

    fun disconnect() {
        state.disconnect()
    }

    /** Detach this Activity's callbacks while retaining a pending resolution for its successor. */
    fun close() {
        closed = true
    }

    fun savePendingState(): Bundle {
        val snapshot = state.snapshot()
        return Bundle().apply {
            putInt("generation", snapshot.generation)
            snapshot.pendingGeneration?.let { putInt("pendingGeneration", it) }
        }
    }

    fun restorePendingState(bundle: Bundle?) {
        if (bundle == null) return
        state.restore(
            GoogleAuthorizationState.PendingSnapshot(
                generation = bundle.getInt("generation"),
                pendingGeneration = if (bundle.containsKey("pendingGeneration")) {
                    bundle.getInt("pendingGeneration")
                } else null,
            ),
        )
    }

    private fun deliver(
        result: AuthorizationResult,
        requestGeneration: Int,
        onAuthorized: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val token = result.accessToken
        if (token.isNullOrBlank() || YOUTUBE_READONLY !in result.grantedScopes) {
            onError("YouTube 읽기 권한이 필요합니다. 다시 연결하고 권한을 허용해 주세요.")
            return
        }
        if (state.acceptAuthorization(requestGeneration, token)) onAuthorized(token)
    }

    companion object {
        const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
    }
}

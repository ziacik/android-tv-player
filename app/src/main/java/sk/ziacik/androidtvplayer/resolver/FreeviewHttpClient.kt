package sk.ziacik.androidtvplayer.resolver

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

interface FreeviewHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String
}

class OkHttpFreeviewClient(
    private val client: OkHttpClient = OkHttpClient(),
) : FreeviewHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String =
        execute(Request.Builder().url(url).applyHeaders(headers).build())

    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String =
        execute(
            Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .applyHeaders(headers)
                .build(),
        )

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        val completed = AtomicBoolean(false)
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) call.cancel()
        }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (completed.compareAndSet(false, true)) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            it.body.string()
                        }
                        if (completed.compareAndSet(false, true)) continuation.resumeWith(Result.success(body))
                    } catch (error: Exception) {
                        if (completed.compareAndSet(false, true)) continuation.resumeWith(Result.failure(error))
                    }
                }
            },
        )
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder = apply {
        headers.forEach { (name, value) -> header(name, value) }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

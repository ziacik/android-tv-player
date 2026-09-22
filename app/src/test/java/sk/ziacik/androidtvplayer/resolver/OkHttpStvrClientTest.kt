package sk.ziacik.androidtvplayer.resolver

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpStvrClientTest {
    @Test
    fun `cancelling coroutine cancels active OkHttp call`() = runBlocking {
        val requestStarted = CountDownLatch(1)
        val callCancelled = CountDownLatch(1)
        val allowInterceptorToReturn = CountDownLatch(1)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestStarted.countDown()
                while (
                    !chain.call().isCanceled() &&
                    !allowInterceptorToReturn.await(10, TimeUnit.MILLISECONDS)
                ) {
                    // Wait until cancellation reaches the actual OkHttp call.
                }
                if (chain.call().isCanceled()) callCancelled.countDown()
                throw IOException("request stopped")
            }
            .build()
        val request = launch(Dispatchers.Default) {
            OkHttpStvrClient(okHttpClient).get("https://example.com", emptyMap())
        }

        assertTrue("request did not start", requestStarted.await(5, TimeUnit.SECONDS))
        request.cancel()
        try {
            assertTrue(
                "coroutine cancellation did not cancel the OkHttp call",
                callCancelled.await(1, TimeUnit.SECONDS),
            )
        } finally {
            allowInterceptorToReturn.countDown()
            request.cancelAndJoin()
        }
    }
}

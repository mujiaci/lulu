package me.rerere.common.http

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.reflect.KClass

class RequestTest {
    @Test
    fun awaitCancelsUnderlyingCallWhenCoroutineIsCancelled() = runBlocking {
        val call = FakeCall()

        val job = launch {
            call.await()
        }
        yield()

        job.cancelAndJoin()

        assertTrue(call.cancelled)
    }

    private class FakeCall : Call {
        var cancelled: Boolean = false
            private set

        override fun request(): Request = Request.Builder()
            .url("https://example.com")
            .build()

        override fun execute(): Response {
            throw IOException("Not used")
        }

        override fun enqueue(responseCallback: Callback) = Unit

        override fun cancel() {
            cancelled = true
        }

        override fun isExecuted(): Boolean = false

        override fun isCanceled(): Boolean = cancelled

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall()

        override fun <T : Any> tag(type: KClass<T>): T? = null

        override fun <T> tag(type: Class<out T>): T? = null

        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()

        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }
}

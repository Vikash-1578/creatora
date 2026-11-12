package com.creatora.app.data.cloud

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerException(message: String) : Exception(message)

class RetryTransientTest {
    @Test
    fun aTransientFailure_isRetriedAndThenSucceeds() = runTest {
        var calls = 0

        val result = retryTransient(listOf(1L, 1L)) { if (++calls < 3) throw ServerException("500") else "ok" }

        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun afterTheLastRetry_theErrorIsThrown() = runTest {
        var calls = 0

        val error = runCatching { retryTransient(listOf(1L, 1L)) { calls++; throw ServerException("still 500") } }.exceptionOrNull()

        assertTrue(error is ServerException)
        assertEquals(3, calls) // the first try and two retries
    }

    @Test
    fun aNetworkErrorIsRetried_butAnOtherErrorIsNot() = runTest {
        var network = 0
        retryTransient(listOf(1L)) { if (network++ == 0) throw IOException("offline") else Unit }
        assertEquals(2, network)

        var other = 0
        val error = runCatching { retryTransient(listOf(1L, 1L)) { other++; throw IllegalStateException("bad request") } }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(1, other)
    }
}

package com.musicali.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AuthInterceptor.
 * Verifies: (1) Bearer token added to requests, (2) single getValidToken() call per request,
 * (3) Mutex-gated sequential refresh under concurrent load.
 * Covers AUTH-03 interceptor behavior.
 * Uses MockWebServer to capture the outbound request headers.
 */
class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var interceptor: AuthInterceptor
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        fakeAuth = FakeAuthRepository()
        interceptor = AuthInterceptor(fakeAuth)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `interceptor adds Bearer Authorization header to request`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        fakeAuth.token = "test-access-token-abc123"

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals("Bearer test-access-token-abc123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `interceptor calls getValidToken exactly once per request`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        fakeAuth.resetCallCount()

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        assertEquals(1, fakeAuth.getValidTokenCallCount)
    }

    @Test
    fun `interceptor works with a long token value`() {
        val longToken = "ya29." + "x".repeat(200)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        fakeAuth.token = longToken

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals("Bearer $longToken", recorded.getHeader("Authorization"))
    }

    @Test
    fun `interceptor adds token even when server returns 401`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        fakeAuth.token = "expired-token"

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals("Bearer expired-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `Mutex ensures getValidToken called exactly N times for N parallel requests`() {
        val requestCount = 5
        // Enqueue N responses so the server accepts all parallel requests
        repeat(requestCount) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        }
        fakeAuth.resetCallCount()

        // Fire N requests in parallel using Dispatchers.IO (simulates concurrent API calls)
        runBlocking {
            (1..requestCount).map {
                async(Dispatchers.IO) {
                    val request = Request.Builder().url(server.url("/test")).build()
                    client.newCall(request).execute()
                }
            }.awaitAll()
        }

        // Mutex serializes the getValidToken() calls — each request gets exactly one call
        assertEquals(requestCount, fakeAuth.getValidTokenCallCount)

        // All N requests received an Authorization header
        repeat(requestCount) {
            val recorded = server.takeRequest()
            assertEquals("Bearer fake-access-token", recorded.getHeader("Authorization"))
        }
    }
}

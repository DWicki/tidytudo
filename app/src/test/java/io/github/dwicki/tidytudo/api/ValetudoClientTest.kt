package io.github.dwicki.tidytudo.api

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValetudoClientTest {
    private val server = MockWebServer()
    private lateinit var baseUrl: String

    @Before
    fun start() {
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun stop() = server.shutdown()

    private fun client(username: String? = null, password: String? = null) =
        ValetudoClient(baseUrl, HttpClientFactory.create(Endpoint(baseUrl, username, password)))

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `version is read from the v2 api`() = runBlocking {
        server.enqueue(json("""{"release":"2026.08.0","commit":"abc"}"""))
        val result = client().version()
        assertEquals("2026.08.0", (result as ApiResult.Success).value.release)
        assertEquals("/api/v2/valetudo/version", server.takeRequest().path)
    }

    @Test
    fun `basic auth header is sent and 401 maps to AuthRequired`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Invalid credentials"))
        val result = client("demo", "secret").version()
        assertEquals(ApiResult.Failure(ApiError.AuthRequired), result)
        assertEquals("Basic ZGVtbzpzZWNyZXQ=", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `missing endpoint maps to NotSupported and bad json to UnexpectedResponse`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json("not json"))
        assertEquals(ApiResult.Failure(ApiError.NotSupported), client().version())
        assertTrue((client().version() as ApiResult.Failure).error is ApiError.UnexpectedResponse)
    }

    @Test
    fun `room cleaning sends the documented body`() = runBlocking {
        server.enqueue(MockResponse().setBody("OK"))
        val result = client().cleanSegments(listOf("4", "1"), iterations = 2, customOrder = true)
        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v2/robot/capabilities/MapSegmentationCapability", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("start_segment_action", body["action"]!!.jsonPrimitive.content)
        assertEquals("[\"4\",\"1\"]", body["segment_ids"].toString())
        assertEquals("2", body["iterations"].toString())
        assertEquals("true", body["customOrder"].toString())
    }

    @Test
    fun `consumable reset uses type and sub type in the path`() = runBlocking {
        server.enqueue(MockResponse().setBody("OK"))
        server.enqueue(MockResponse().setBody("OK"))
        client().resetConsumable("brush", "main")
        client().resetConsumable("filter", null)
        assertEquals("/api/v2/robot/capabilities/ConsumableMonitoringCapability/brush/main", server.takeRequest().path)
        assertEquals("/api/v2/robot/capabilities/ConsumableMonitoringCapability/filter", server.takeRequest().path)
    }

    @Test
    fun `timers object is turned into a list and new timers are posted`() = runBlocking {
        server.enqueue(json("""{"a":{"id":"a","enabled":true,"dow":[1],"hour":7,"minute":30,"action":{"type":"full_cleanup","params":{}},"pre_actions":[]}}"""))
        server.enqueue(MockResponse().setBody("OK"))
        val timers = (client().timers() as ApiResult.Success).value
        assertEquals("a", timers.single().id)
        client().saveTimer(timers.single().copy(id = ""))
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertTrue(post.body.readUtf8().contains("\"pre_actions\":[]"))
    }

    @Test
    fun `sse stream delivers named events`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(": sse-keep-alive\nevent: StateAttributesUpdated\ndata: []\n\n"),
        )
        val event = client().sse.events(SseConnector.ATTRIBUTES).first()
        assertEquals(SseEvent("StateAttributesUpdated", "[]"), event)
        assertEquals("text/event-stream", server.takeRequest().getHeader("Accept"))
    }

}

package com.jorisjonkers.personalstack.systemtests

import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * System test: register -> login -> verify -> create conversation through Traefik.
 * Validates the full auth-api -> Traefik forward-auth -> assistant-api flow.
 *
 * The assistant API is protected by Traefik forward-auth, so the assistant calls
 * must carry the authenticated session cookie and let Traefik inject X-User-Id.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossServiceSystemTest {
    private val authBaseUrl = TestHelper.authBaseUrl
    private val assistantBaseUrl = TestHelper.assistantBaseUrl

    private fun registerAndAuthenticate(): AuthenticatedSession {
        val username = "cross_${java.util.UUID.randomUUID().toString().take(8)}"
        val user = TestHelper.registerAndConfirm(username)
        TestHelper.grantServicePermission(username, "ASSISTANT")
        val session = TestHelper.sessionLogin(user)
        val userId = verifyAndGetUserId(session.sessionCookie)
        return AuthenticatedSession(session.sessionCookie, userId)
    }

    private fun verifyAndGetUserId(sessionCookie: String): String {
        val userId =
            TestHelper.givenApi()
                .baseUri(authBaseUrl)
                .cookie("SESSION", sessionCookie)
                .`when`()
                .get("/api/v1/auth/verify")
                .then()
                .statusCode(200)
                .extract()
                .header("X-User-Id")
        assertThat(userId).isNotBlank()
        return userId
    }

    @Test
    fun `full flow register login create conversation`() {
        val session = registerAndAuthenticate()
        val conversationId = createConversation(session.sessionCookie)
        sendMessage(session.sessionCookie, conversationId)
        val messages = getMessages(session.sessionCookie, conversationId)
        assertThat(messages).isNotEmpty()
        assertThat(session.userId).isNotBlank()
    }

    private fun createConversation(sessionCookie: String): String {
        val conversationId =
            TestHelper.givenApi()
                .baseUri(assistantBaseUrl)
                .cookie("SESSION", sessionCookie)
                .contentType(ContentType.JSON)
                .body("""{"title":"My first conversation"}""")
                .`when`()
                .post("/api/v1/conversations")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id")
        assertThat(conversationId).isNotBlank()
        return conversationId
    }

    private fun sendMessage(
        sessionCookie: String,
        conversationId: String,
    ) {
        TestHelper.givenApi()
            .baseUri(assistantBaseUrl)
            .cookie("SESSION", sessionCookie)
            .contentType(ContentType.JSON)
            .body("""{"content":"Hello from system test","role":"USER"}""")
            .`when`()
            .post("/api/v1/conversations/$conversationId/messages")
            .then()
            .statusCode(201)
    }

    private fun getMessages(
        sessionCookie: String,
        conversationId: String,
    ): List<Map<String, Any>> =
        TestHelper.givenApi()
            .baseUri(assistantBaseUrl)
            .cookie("SESSION", sessionCookie)
            .`when`()
            .get("/api/v1/conversations/$conversationId/messages")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("")

    private data class AuthenticatedSession(
        val sessionCookie: String,
        val userId: String,
    )
}

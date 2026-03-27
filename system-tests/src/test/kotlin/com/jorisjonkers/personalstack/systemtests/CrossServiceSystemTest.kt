package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: register -> login -> verify -> create conversation via assistant-api.
 * Validates the full cross-service flow (auth-api -> assistant-api).
 *
 * Without Traefik in the loop, the test simulates the forward-auth flow:
 * it calls /api/v1/auth/verify to get X-User-Id, then passes it to assistant-api.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossServiceSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")
    private val assistantBaseUrl = System.getProperty("test.assistant-api.url", "http://localhost:8082")

    private fun registerAndAuthenticate(): String {
        val username = "cross_${UUID.randomUUID().toString().take(8)}"
        registerUser(username)
        val accessToken = login(username)
        return verifyAndGetUserId(accessToken)
    }

    private fun registerUser(username: String) {
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$username@cross.example.com","password":"CrossTest1!"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)
    }

    private fun login(username: String): String =
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"CrossTest1!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("accessToken")

    private fun verifyAndGetUserId(accessToken: String): String {
        val userId =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $accessToken")
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
        val userId = registerAndAuthenticate()
        val conversationId = createConversation(userId)
        sendMessage(userId, conversationId)
        val messages = getMessages(userId, conversationId)
        assertThat(messages).isNotEmpty()
    }

    private fun createConversation(userId: String): String {
        val conversationId =
            given()
                .baseUri(assistantBaseUrl)
                .contentType(ContentType.JSON)
                .header("X-User-Id", userId)
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
        userId: String,
        conversationId: String,
    ) {
        given()
            .baseUri(assistantBaseUrl)
            .contentType(ContentType.JSON)
            .header("X-User-Id", userId)
            .body("""{"content":"Hello from system test","role":"USER"}""")
            .`when`()
            .post("/api/v1/conversations/$conversationId/messages")
            .then()
            .statusCode(201)
    }

    private fun getMessages(
        userId: String,
        conversationId: String,
    ): List<Map<String, Any>> =
        given()
            .baseUri(assistantBaseUrl)
            .header("X-User-Id", userId)
            .`when`()
            .get("/api/v1/conversations/$conversationId/messages")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("")
}

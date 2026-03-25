package com.jorisjonkers.privatestack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: register → login → create conversation via assistant-api.
 * Validates the full cross-service flow (auth-api → assistant-api).
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossServiceSystemTest {

    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")
    private val assistantBaseUrl = System.getProperty("test.assistant-api.url", "http://localhost:8082")

    @Test
    fun `full flow register login create conversation`() {
        val username = "cross_${UUID.randomUUID().toString().take(8)}"
        val email = "$username@cross.example.com"
        val password = "CrossTest1!"

        // Register
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$email","password":"$password"}""")
            .`when`().post("/api/v1/users/register")
            .then().statusCode(201)

        // Login — get access token
        val tokenResponse = given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"$password"}""")
            .`when`().post("/api/v1/auth/login")
            .then().statusCode(200)
            .extract().jsonPath()

        val accessToken = tokenResponse.getString("accessToken")
        assertThat(accessToken).isNotBlank()

        // Create conversation via assistant-api (with Bearer token)
        val convResponse = given()
            .baseUri(assistantBaseUrl)
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer $accessToken")
            .body("""{"title":"My first conversation"}""")
            .`when`().post("/api/v1/conversations")
            .then().statusCode(201)
            .extract().jsonPath()

        val conversationId = convResponse.getString("id")
        assertThat(conversationId).isNotBlank()

        // Send a message in the conversation
        given()
            .baseUri(assistantBaseUrl)
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer $accessToken")
            .body("""{"content":"Hello from system test","role":"USER"}""")
            .`when`().post("/api/v1/conversations/$conversationId/messages")
            .then().statusCode(201)

        // List messages — should contain our message
        val messagesResponse = given()
            .baseUri(assistantBaseUrl)
            .header("Authorization", "Bearer $accessToken")
            .`when`().get("/api/v1/conversations/$conversationId/messages")
            .then().statusCode(200)
            .extract().jsonPath()

        val messages: List<Map<String, Any>> = messagesResponse.getList("")
        assertThat(messages).isNotEmpty()
    }
}

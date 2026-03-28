package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import java.sql.DriverManager
import java.util.UUID

/**
 * Shared helper for system tests that need to register a user and obtain a token.
 * Since login requires email confirmation, this helper retrieves the confirmation
 * token from the database and confirms the email before logging in.
 */
object TestHelper {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")
    private val dbUrl = System.getProperty("test.db.url", "jdbc:postgresql://localhost:5432/auth_db")
    private val dbUser = System.getProperty("test.db.user", "auth_user")
    private val dbPassword = System.getProperty("test.db.password", "auth_password")

    fun registerAndConfirm(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): RegisteredUser {
        val email = "$username@systemtest.example.com"

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$email","password":"$password"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        val token = getConfirmationTokenFromDb(username)

        given()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/auth/confirm-email?token=$token")
            .then()
            .statusCode(200)

        return RegisteredUser(username, email, password)
    }

    fun loginAndGetToken(user: RegisteredUser): String =
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"${user.username}","password":"${user.password}"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("accessToken")

    fun registerConfirmAndLogin(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): String {
        val user = registerAndConfirm(username, password)
        return loginAndGetToken(user)
    }

    private fun getConfirmationTokenFromDb(username: String): String =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn
                .prepareStatement(
                    """
                    SELECT ect.token FROM email_confirmation_token ect
                    JOIN app_user u ON u.id = ect.user_id
                    WHERE u.username = ? AND ect.used_at IS NULL
                    ORDER BY ect.created_at DESC LIMIT 1
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, username)
                    val rs = stmt.executeQuery()
                    require(rs.next()) { "No confirmation token found for user: $username" }
                    rs.getString("token")
                }
        }

    data class RegisteredUser(
        val username: String,
        val email: String,
        val password: String,
    )
}

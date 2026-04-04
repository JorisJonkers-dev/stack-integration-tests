package com.jorisjonkers.personalstack.systemtests

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import org.apache.commons.codec.binary.Base32
import org.assertj.core.api.Assertions.assertThat
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Shared helper for system tests that need to register a user and obtain a session.
 * Since login requires email confirmation, this helper retrieves the confirmation
 * token from the database and confirms the email before logging in.
 *
 * Protected endpoints use session-based auth (SESSION cookie via /session-login).
 * The JWT login endpoint (/login) is kept for tests that verify JWT token contents.
 */
object TestHelper {
    private const val TOTP_STEP_SECONDS = 30L
    private const val TOTP_MIN_VALIDITY_SECONDS = 5L

    val authBaseUrl = System.getProperty("test.auth-api.url", "https://auth.jorisjonkers.test")
    val assistantBaseUrl = System.getProperty("test.assistant-api.url", "https://assistant.jorisjonkers.test")

    fun givenApi(): RequestSpecification = given().relaxedHTTPSValidation()
    private val dbUrl = System.getProperty("test.db.url", "jdbc:postgresql://localhost:5432/auth_db")
    private val dbUser = System.getProperty("test.db.user", "auth_user")
    private val dbPassword = System.getProperty("test.db.password", "auth_password")

    fun registerAndConfirm(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): RegisteredUser {
        val email = "$username@systemtest.example.com"

        givenApi()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$email","firstName":"Test","lastName":"User","password":"$password"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        val token = getConfirmationTokenFromDb(username)

        givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/auth/confirm-email?token=$token")
            .then()
            .statusCode(200)

        return RegisteredUser(username, email, password)
    }

    fun loginAndGetToken(user: RegisteredUser): String =
        givenApi()
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

    fun generateFreshTotpCode(secret: String): String {
        val stepMillis = TimeUnit.SECONDS.toMillis(TOTP_STEP_SECONDS)
        val now = System.currentTimeMillis()
        val millisUntilNextStep = stepMillis - (now % stepMillis)
        if (millisUntilNextStep <= TimeUnit.SECONDS.toMillis(TOTP_MIN_VALIDITY_SECONDS)) {
            Thread.sleep(millisUntilNextStep + 250)
        }

        val padded = secret.padEnd((secret.length + 7) / 8 * 8, '=')
        val secretBytes = Base32().decode(padded)
        val config =
            TimeBasedOneTimePasswordConfig(
                codeDigits = 6,
                hmacAlgorithm = HmacAlgorithm.SHA1,
                timeStep = TOTP_STEP_SECONDS,
                timeStepUnit = TimeUnit.SECONDS,
            )
        return TimeBasedOneTimePasswordGenerator(secretBytes, config).generate()
    }

    fun sessionLogin(
        user: RegisteredUser,
        totpCode: String? = null,
    ): SessionInfo {
        val body =
            if (totpCode != null) {
                """{"username":"${user.username}","password":"${user.password}","totpCode":"$totpCode"}"""
            } else {
                """{"username":"${user.username}","password":"${user.password}"}"""
            }

        val response =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body(body)
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(response.statusCode).isEqualTo(200)
        val sessionCookie =
            response.cookie("SESSION")
                ?: throw IllegalStateException("No SESSION cookie in response")
        return SessionInfo(sessionCookie, UUID.randomUUID().toString())
    }

    fun sessionLoginAndGetCookie(
        user: RegisteredUser,
        totpCode: String? = null,
    ): String = sessionLogin(user, totpCode).sessionCookie

    fun registerConfirmAndGetSession(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): SessionUser {
        val user = registerAndConfirm(username, password)
        val session = sessionLogin(user)
        return SessionUser(user, session.sessionCookie, session.csrfToken)
    }

    fun registerConfirmAndGetAdminSession(
        username: String = "adm_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): SessionUser {
        val user = registerAndConfirm(username, password)
        makeUserAdmin(user.username)
        val session = sessionLogin(user)
        return SessionUser(user, session.sessionCookie, session.csrfToken)
    }

    fun registerConfirmAndLogin(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): String {
        val user = registerAndConfirm(username, password)
        return loginAndGetToken(user)
    }

    fun registerConfirmAndLoginAsAdmin(
        username: String = "adm_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): String {
        val user = registerAndConfirm(username, password)
        makeUserAdmin(user.username)
        return loginAndGetToken(user)
    }

    fun makeUserAdmin(username: String) {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn
                .prepareStatement("UPDATE app_user SET role = 'ADMIN' WHERE username = ?")
                .use { stmt ->
                    stmt.setString(1, username)
                    stmt.executeUpdate()
                }
        }
    }

    fun grantServicePermission(
        username: String,
        service: String,
    ) {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn
                .prepareStatement(
                    """
                    INSERT INTO user_service_permissions (user_id, service)
                    SELECT id, ? FROM app_user WHERE username = ?
                    ON CONFLICT DO NOTHING
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, service)
                    stmt.setString(2, username)
                    stmt.executeUpdate()
                }
        }
    }

    fun getConfirmationTokenFromDb(username: String): String =
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

    data class SessionInfo(
        val sessionCookie: String,
        val csrfToken: String,
    )

    data class SessionUser(
        val user: RegisteredUser,
        val sessionCookie: String,
        val csrfToken: String,
    )
}

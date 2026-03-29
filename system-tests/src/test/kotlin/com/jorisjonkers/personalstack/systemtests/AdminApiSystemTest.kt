package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: admin API full round-trip through the running stack.
 *
 * Verifies that an ADMIN user can list users, update roles, manage service permissions,
 * and delete accounts; and that non-admin users are rejected.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminApiSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

    @Test
    fun `admin can list all users`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        val users =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<Map<String, Any>>("$")

        assertThat(users).isNotEmpty()
    }

    @Test
    fun `non-admin user is rejected from admin endpoints`() {
        val userSession = TestHelper.registerConfirmAndGetSession()

        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", userSession.sessionCookie)
            .`when`()
            .get("/api/v1/admin/users")
            .then()
            .statusCode(403)
    }

    @Test
    fun `admin can update user role and service permissions`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()
        val targetUser = TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<Map<String, Any>>("$")

        val targetId = users.first { it["username"] == targetUser.username }["id"] as String

        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .cookie("XSRF-TOKEN", adminSession.csrfToken)
            .header("X-XSRF-TOKEN", adminSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"role":"READONLY"}""")
            .`when`()
            .patch("/api/v1/admin/users/$targetId/role")
            .then()
            .statusCode(200)

        val updated =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users/$targetId")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()

        assertThat(updated.getString("role")).isEqualTo("READONLY")

        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .cookie("XSRF-TOKEN", adminSession.csrfToken)
            .header("X-XSRF-TOKEN", adminSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"services":["GRAFANA","ASSISTANT"]}""")
            .`when`()
            .put("/api/v1/admin/users/$targetId/services")
            .then()
            .statusCode(200)

        val withPerms =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users/$targetId")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<String>("servicePermissions")

        assertThat(withPerms).containsExactlyInAnyOrder("ASSISTANT", "GRAFANA")
    }

    @Test
    fun `admin can delete a user account`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()
        val targetUser = TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<Map<String, Any>>("$")

        val targetId = users.first { it["username"] == targetUser.username }["id"] as String

        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .cookie("XSRF-TOKEN", adminSession.csrfToken)
            .header("X-XSRF-TOKEN", adminSession.csrfToken)
            .`when`()
            .delete("/api/v1/admin/users/$targetId")
            .then()
            .statusCode(204)

        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .`when`()
            .get("/api/v1/admin/users/$targetId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `admin can list users with pagination`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        // Create a few users so the list is non-trivial
        TestHelper.registerAndConfirm()
        TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<Map<String, Any>>("$")

        assertThat(users).hasSizeGreaterThanOrEqualTo(2)

        // Verify each user has expected fields
        users.forEach { user ->
            assertThat(user).containsKey("id")
            assertThat(user).containsKey("username")
            assertThat(user).containsKey("role")
        }
    }

    @Test
    fun `admin can update service permissions`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()
        val targetUser = TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<Map<String, Any>>("$")

        val targetId = users.first { it["username"] == targetUser.username }["id"] as String

        // Set service permissions
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .cookie("XSRF-TOKEN", adminSession.csrfToken)
            .header("X-XSRF-TOKEN", adminSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"services":["GRAFANA","N8N"]}""")
            .`when`()
            .put("/api/v1/admin/users/$targetId/services")
            .then()
            .statusCode(200)

        val perms =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users/$targetId")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<String>("servicePermissions")

        assertThat(perms).containsExactlyInAnyOrder("GRAFANA", "N8N")

        // Update to a different set
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .cookie("XSRF-TOKEN", adminSession.csrfToken)
            .header("X-XSRF-TOKEN", adminSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"services":["VAULT"]}""")
            .`when`()
            .put("/api/v1/admin/users/$targetId/services")
            .then()
            .statusCode(200)

        val updatedPerms =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users/$targetId")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<String>("servicePermissions")

        assertThat(updatedPerms).containsExactly("VAULT")
    }

    @Test
    fun `non-admin user gets 403 on admin endpoints`() {
        val userSession = TestHelper.registerConfirmAndGetSession()

        // GET /admin/users
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", userSession.sessionCookie)
            .`when`()
            .get("/api/v1/admin/users")
            .then()
            .statusCode(403)

        // Attempt to change a role (use a fake UUID)
        val fakeId = UUID.randomUUID().toString()
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", userSession.sessionCookie)
            .cookie("XSRF-TOKEN", userSession.csrfToken)
            .header("X-XSRF-TOKEN", userSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"role":"ADMIN"}""")
            .`when`()
            .patch("/api/v1/admin/users/$fakeId/role")
            .then()
            .statusCode(403)

        // Attempt to update service permissions
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", userSession.sessionCookie)
            .cookie("XSRF-TOKEN", userSession.csrfToken)
            .header("X-XSRF-TOKEN", userSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"services":["GRAFANA"]}""")
            .`when`()
            .put("/api/v1/admin/users/$fakeId/services")
            .then()
            .statusCode(403)
    }

    @Test
    fun `role change reflected in next token`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()
        val targetUser = TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<Map<String, Any>>("$")

        val targetId = users.first { it["username"] == targetUser.username }["id"] as String

        // Change role to READONLY
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .cookie("XSRF-TOKEN", adminSession.csrfToken)
            .header("X-XSRF-TOKEN", adminSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"role":"READONLY"}""")
            .`when`()
            .patch("/api/v1/admin/users/$targetId/role")
            .then()
            .statusCode(200)

        // Login as the target user and get a fresh token
        val newToken = TestHelper.loginAndGetToken(targetUser)
        assertThat(newToken).isNotBlank()

        // Decode JWT payload to check roles claim
        val payloadJson = decodeJwtPayload(newToken)
        assertThat(payloadJson).contains("ROLE_READONLY")
    }

    @Test
    fun `service permission grant reflected in next token`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()
        val targetUser = TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .`when`()
                .get("/api/v1/admin/users")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList<Map<String, Any>>("$")

        val targetId = users.first { it["username"] == targetUser.username }["id"] as String

        // Grant GRAFANA permission
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", adminSession.sessionCookie)
            .cookie("XSRF-TOKEN", adminSession.csrfToken)
            .header("X-XSRF-TOKEN", adminSession.csrfToken)
            .contentType(ContentType.JSON)
            .body("""{"services":["GRAFANA"]}""")
            .`when`()
            .put("/api/v1/admin/users/$targetId/services")
            .then()
            .statusCode(200)

        // Login as the target user and get a fresh token
        val newToken = TestHelper.loginAndGetToken(targetUser)
        assertThat(newToken).isNotBlank()

        // Decode JWT payload to check roles claim includes the service permission
        val payloadJson = decodeJwtPayload(newToken)
        assertThat(payloadJson).contains("SERVICE_GRAFANA")
    }

    private fun decodeJwtPayload(token: String): String {
        val parts = token.split(".")
        require(parts.size >= 2) { "Invalid JWT format" }
        return String(
            java.util.Base64
                .getUrlDecoder()
                .decode(parts[1]),
        )
    }
}

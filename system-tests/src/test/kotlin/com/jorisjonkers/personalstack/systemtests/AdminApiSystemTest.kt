package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

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
        val adminToken = TestHelper.registerConfirmAndLoginAsAdmin()

        val users =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $adminToken")
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
        val userToken = TestHelper.registerConfirmAndLogin()

        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $userToken")
            .`when`()
            .get("/api/v1/admin/users")
            .then()
            .statusCode(403)
    }

    @Test
    fun `admin can update user role and service permissions`() {
        val adminToken = TestHelper.registerConfirmAndLoginAsAdmin()
        val targetUser = TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $adminToken")
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
            .header("Authorization", "Bearer $adminToken")
            .contentType(ContentType.JSON)
            .body("""{"role":"READONLY"}""")
            .`when`()
            .patch("/api/v1/admin/users/$targetId/role")
            .then()
            .statusCode(200)

        val updated =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $adminToken")
                .`when`()
                .get("/api/v1/admin/users/$targetId")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()

        assertThat(updated.getString("role")).isEqualTo("READONLY")

        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $adminToken")
            .contentType(ContentType.JSON)
            .body("""{"services":["GRAFANA","ASSISTANT"]}""")
            .`when`()
            .put("/api/v1/admin/users/$targetId/services")
            .then()
            .statusCode(200)

        val withPerms =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $adminToken")
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
        val adminToken = TestHelper.registerConfirmAndLoginAsAdmin()
        val targetUser = TestHelper.registerAndConfirm()

        val users =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $adminToken")
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
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .delete("/api/v1/admin/users/$targetId")
            .then()
            .statusCode(204)

        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $adminToken")
            .`when`()
            .get("/api/v1/admin/users/$targetId")
            .then()
            .statusCode(404)
    }
}

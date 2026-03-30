package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class N8nOidcSystemTest {
    @Test
    fun `n8n delegates login to auth service after forward-auth passes`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        val response =
            given()
                .relaxedHTTPSValidation()
                .baseUri("https://n8n.jorisjonkers.test")
                .cookie("SESSION", adminSession.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/auth/oidc/login")

        assertThat(response.statusCode).isEqualTo(302)
        assertThat(response.header("Location"))
            .contains("https://auth.jorisjonkers.test/api/oauth2/authorize")
            .contains("client_id=n8n")
    }
}

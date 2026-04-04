package com.jorisjonkers.personalstack.systemtests

import io.restassured.filter.cookie.CookieFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrafanaOidcSystemTest {
    @Test
    fun `grafana delegates login to auth service through oidc`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()
        val cookies = CookieFilter()

        val oauthRedirect =
            TestHelper.givenApi()
                .filter(cookies)
                .baseUri("https://grafana.jorisjonkers.test")
                .cookie("SESSION", adminSession.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/login/generic_oauth")

        assertThat(oauthRedirect.statusCode).isEqualTo(302)
        val authorizeLocation = oauthRedirect.header("Location")
        assertThat(authorizeLocation)
            .contains("https://auth.jorisjonkers.test/api/oauth2/authorize")
            .contains("client_id=grafana")
    }
}

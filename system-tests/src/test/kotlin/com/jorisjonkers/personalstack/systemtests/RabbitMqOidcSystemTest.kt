package com.jorisjonkers.personalstack.systemtests

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RabbitMqOidcSystemTest {
    @Test
    fun `rabbitmq management serves the ui for service users once oidc completes`() {
        val rabbitMqSession = TestHelper.registerConfirmGrantAndGetSession("RABBITMQ")

        TestHelper.givenApi()
            .baseUri("https://rabbitmq.jorisjonkers.test")
            .cookie("SESSION", rabbitMqSession.sessionCookie)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
            .body(containsString("RabbitMQ"))
            .body(not(containsString("Not authorized")))
    }

    @Test
    fun `rabbitmq management serves the ui for admin users`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        TestHelper.givenApi()
            .baseUri("https://rabbitmq.jorisjonkers.test")
            .cookie("SESSION", adminSession.sessionCookie)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
            .body(containsString("RabbitMQ"))
            .body(not(containsString("Not authorized")))
    }
}

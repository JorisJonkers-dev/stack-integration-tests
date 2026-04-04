package com.jorisjonkers.personalstack.systemtests

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RabbitMqOidcSystemTest {
    @Test
    fun `rabbitmq management serves the ui once the service session is established`() {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        TestHelper.givenApi()
            .baseUri("https://rabbitmq.jorisjonkers.test")
            .cookie("SESSION", adminSession.sessionCookie)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
            .body(containsString("RabbitMQ"))
    }
}

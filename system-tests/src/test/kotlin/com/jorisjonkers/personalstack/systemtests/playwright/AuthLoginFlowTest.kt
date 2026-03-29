package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class AuthLoginFlowTest : PlaywrightTestBase() {
    @Test
    fun `successful login redirects away from login page`() {
        val user = registerAndConfirm()

        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        page.waitForURL(
            { !it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(15000.0),
        )
    }

    @Test
    fun `login with wrong password shows error`() {
        val user = registerAndConfirm()

        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(user.username)
        page.locator("#password").fill("WrongPassword1!")
        page.locator("button[type='submit']").click()

        page.waitForSelector(
            ".text-red-400, .text-red-500, [role='alert']",
            Page.WaitForSelectorOptions().setTimeout(10000.0),
        )
        assertThat(page.locator(".text-red-400, .text-red-500, [role='alert']").first()).isVisible()
    }

    @Test
    fun `login with unconfirmed email shows error`() {
        val username = uniqueUsername("unconf")
        val email = "$username@systemtest.example.com"

        io.restassured.RestAssured
            .given()
            .baseUri(System.getProperty("test.auth-api.url", "http://localhost:8081"))
            .contentType(io.restassured.http.ContentType.JSON)
            .body("""{"username":"$username","email":"$email","password":"Test1234!"}""")
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(username)
        page.locator("#password").fill("Test1234!")
        page.locator("button[type='submit']").click()

        page.waitForSelector(
            ".text-red-400, .text-red-500, [role='alert']",
            Page.WaitForSelectorOptions().setTimeout(10000.0),
        )
        assertThat(page.locator("body")).containsText("confirm")
    }

    @Test
    fun `authenticated user visiting login is redirected`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$AUTH_UI_URL/login")
        page.waitForURL(
            { !it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )
    }

    @Test
    fun `register page is accessible from login`() {
        page.navigate("$AUTH_UI_URL/login")

        page.locator("a[href='/register']").click()
        page.waitForURL("**/register")
        assertThat(page.locator("body")).containsText("Create account")
    }
}

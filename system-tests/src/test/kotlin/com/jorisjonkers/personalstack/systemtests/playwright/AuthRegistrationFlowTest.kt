package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.restassured.http.ContentType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class AuthRegistrationFlowTest : PlaywrightTestBase() {
    @Test
    fun `register navigates to check email page`() {
        val username = uniqueUsername("reg")
        val email = "$username@systemtest.example.com"

        page.navigate("$AUTH_UI_URL/register")
        page.locator("#username").fill(username)
        page.locator("#email").fill(email)
        page.locator("#firstName").fill("Test")
        page.locator("#lastName").fill("User")
        page.locator("#password").fill("Test1234!")
        page.locator("#confirmPassword").fill("Test1234!")
        page.locator("button[type='submit']").click()

        page.waitForURL("**/check-email**")
        assertThat(page.locator("body")).containsText(email)
    }

    @Test
    fun `register with duplicate username shows error`() {
        val user = registerAndConfirm()

        page.navigate("$AUTH_UI_URL/register")
        page.locator("#username").fill(user.username)
        page.locator("#email").fill("dup_${user.username}@test.com")
        page.locator("#firstName").fill("Test")
        page.locator("#lastName").fill("User")
        page.locator("#password").fill("Test1234!")
        page.locator("#confirmPassword").fill("Test1234!")
        page.locator("button[type='submit']").click()

        assertThat(page.locator(".text-red-400, .text-red-500, [role='alert']").first()).isVisible()
    }

    @Test
    fun `register form validates password match`() {
        page.navigate("$AUTH_UI_URL/register")
        page.locator("#username").fill("testuser123")
        page.locator("#email").fill("test@test.com")
        page.locator("#firstName").fill("Test")
        page.locator("#lastName").fill("User")
        page.locator("#password").fill("Test1234!")
        page.locator("#confirmPassword").fill("Different1!")
        page.locator("button[type='submit']").click()

        assertThat(page.locator("body")).containsText("match")
    }

    @Test
    fun `confirm email page shows success`() {
        val username = uniqueUsername("conf")
        val user = registerAndConfirm(username)

        // Register a NEW user (unconfirmed) and get token from DB
        val newUsername = uniqueUsername("conf2")
        val email = "$newUsername@systemtest.example.com"
        TestHelper.givenApi()
            .baseUri(TestHelper.authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$newUsername","email":"$email","firstName":"Test","lastName":"User","password":"Test1234!"}""")
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        val token = TestHelper.getConfirmationTokenFromDb(newUsername)
        page.navigate("$AUTH_UI_URL/confirm-email?token=$token")

        page.waitForSelector("text=confirmed", Page.WaitForSelectorOptions().setTimeout(10000.0))
        assertThat(page.locator("body")).containsText("confirmed")
    }

    @Test
    fun `confirm email with invalid token shows error`() {
        page.navigate("$AUTH_UI_URL/confirm-email?token=invalid-token-12345")

        page.waitForTimeout(5000.0)
        val bodyText = page.locator("body").textContent().lowercase()
        org.assertj.core.api.Assertions
            .assertThat(bodyText)
            .satisfiesAnyOf(
                {
                    org.assertj.core.api.Assertions
                        .assertThat(it)
                        .contains("failed")
                },
                {
                    org.assertj.core.api.Assertions
                        .assertThat(it)
                        .contains("invalid")
                },
                {
                    org.assertj.core.api.Assertions
                        .assertThat(it)
                        .contains("expired")
                },
                {
                    org.assertj.core.api.Assertions
                        .assertThat(it)
                        .contains("error")
                },
            )
    }

    @Test
    fun `check email page shows email and resend option`() {
        val email = "check_test@systemtest.example.com"
        page.navigate("$AUTH_UI_URL/check-email?email=$email")

        assertThat(page.locator("body")).containsText(email)
        assertThat(page.locator("text=Resend")).isVisible()
    }

    @Test
    fun `login page has link to register`() {
        page.navigate("$AUTH_UI_URL/login")

        val registerLink = page.locator("a[href='/register']")
        assertThat(registerLink).isVisible()
    }
}

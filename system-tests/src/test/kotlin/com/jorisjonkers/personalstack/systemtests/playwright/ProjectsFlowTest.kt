package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Project create + repository linker, end-to-end through PR #378's
 * redesigned UI. The repository pool is M:N now — this test
 * exercises the new ProjectRepositoryPicker affordance, not the
 * legacy AddLinkForm.
 */
@Tag("system")
class ProjectsFlowTest : PlaywrightTestBase() {
    @Test
    fun `user can create a project and land on its detail page`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        val slug = "pt-${UUID.randomUUID().toString().take(8)}"
        page.navigate("$ASSISTANT_UI_URL/projects")
        page.waitForLoadState()

        page.locator("[data-testid='projects-new-button']").click()
        page.locator("[data-testid='proj-name']").fill("Project $slug")
        // The slug auto-fills from the name; assert it then overwrite
        // with a deterministic value.
        page.locator("[data-testid='proj-slug']").fill(slug)
        page.locator("[data-testid='proj-create-submit']").click()

        // Successful create navigates to /projects/{id}.
        page.waitForURL { it.contains("/projects/") && !it.endsWith("/projects") }
        assertThat(page.locator("body")).containsText("Project $slug")
        assertThat(page.locator("body")).containsText("project:$slug")
    }

    @Test
    fun `user can link a repository to a project and unlink it`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        // 1. Create a repository the project can link.
        val repoName = "pt-repo-${UUID.randomUUID().toString().take(8)}"
        page.navigate("$ASSISTANT_UI_URL/repositories")
        page.waitForLoadState()
        page.locator("[data-testid='repositories-new-button']").click()
        page.locator("[data-testid='repo-name']").fill(repoName)
        page.locator("[data-testid='repo-url']").fill("git@github.com:owner/$repoName.git")
        page.locator("[data-testid='repo-create-submit']").click()
        page.waitForURL { it.contains("/repositories/") }

        // 2. Create a project.
        val slug = "pt-${UUID.randomUUID().toString().take(8)}"
        page.navigate("$ASSISTANT_UI_URL/projects")
        page.waitForLoadState()
        page.locator("[data-testid='projects-new-button']").click()
        page.locator("[data-testid='proj-name']").fill("Project $slug")
        page.locator("[data-testid='proj-slug']").fill(slug)
        page.locator("[data-testid='proj-create-submit']").click()
        page.waitForURL { it.contains("/projects/") }

        // 3. Open the picker, select the repo, link it.
        page.locator("[data-testid='project-link-repo-button']").click()
        // The picker lists every repository the project hasn't already
        // linked; our newly-created one is in there.
        page.locator("input[type='radio']").first().click()
        page.locator("[data-testid='picker-submit']").click()

        assertThat(page.locator("[data-testid='project-repositories-list']")).containsText(repoName)
    }
}

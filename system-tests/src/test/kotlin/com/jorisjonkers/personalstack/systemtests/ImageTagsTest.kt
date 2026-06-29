package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class ImageTagsTest {
    @Test
    fun `parses explicit service tags`() {
        val tags =
            stackImageTags(
                "auth-api=v0.1.0 auth-ui=v0.1.0 home-portal=v0.1.0 knowledge-api=v0.1.0 " +
                    "agents-api=v0.16.0 agents-ui=v0.16.0 agent-runtime=v0.16.0",
            )

        assertThat(tags.tagFor("auth-api")).isEqualTo("v0.1.0")
        assertThat(tags.tagFor("agents-ui")).isEqualTo("v0.16.0")
    }

    @Test
    fun `parses exact image refs from deployment lock image output`() {
        val tags =
            stackImageTags(
                """
                ghcr.io/jorisjonkers-dev/auth-api:v0.1.0
                ghcr.io/jorisjonkers-dev/auth-ui:v0.1.0
                ghcr.io/jorisjonkers-dev/home-portal:v0.1.0
                ghcr.io/jorisjonkers-dev/knowledge-api:v0.1.0
                ghcr.io/jorisjonkers-dev/agents-api:v0.16.0
                ghcr.io/jorisjonkers-dev/agents-ui:v0.16.0
                ghcr.io/jorisjonkers-dev/agent-runtime:v0.16.0
                ghcr.io/twin/gatus:v5.20.0
                """.trimIndent(),
            )

        assertThat(tags.tagFor("auth-api")).isEqualTo("ghcr.io/jorisjonkers-dev/auth-api:v0.1.0")
        assertThat(tags.tagFor("agents-ui")).isEqualTo("ghcr.io/jorisjonkers-dev/agents-ui:v0.16.0")
        assertThat(tags.tagFor("gatus")).isNull()
    }

    @Test
    fun `requires every private deploy-gate service`() {
        val tags =
            stackImageTags(
                """
                ghcr.io/jorisjonkers-dev/auth-api:v0.1.0
                ghcr.io/jorisjonkers-dev/auth-ui:v0.1.0
                ghcr.io/jorisjonkers-dev/home-portal:v0.1.0
                ghcr.io/jorisjonkers-dev/knowledge-api:v0.1.0
                ghcr.io/jorisjonkers-dev/agents-api:v0.16.0
                ghcr.io/jorisjonkers-dev/agents-ui:v0.16.0
                ghcr.io/jorisjonkers-dev/agent-runtime:v0.16.0
                ghcr.io/twin/gatus:v5.20.0
                """.trimIndent(),
            )

        assertThat(tags.requireAll(stackImageServices)).isSameAs(tags)
    }

    @Test
    fun `loads workflow image tags from the environment contract`() {
        val tags = stackImageTagsFromEnvironment()

        if (System.getenv("IMAGE_TAGS").isNullOrBlank() && System.getProperty("test.image-tags").isNullOrBlank()) {
            assertThat(tags.tags).isEmpty()
        } else {
            assertThat(tags.requireAll(stackImageServices)).isSameAs(tags)
        }
    }

    @Test
    fun `rejects latest tags`() {
        assertThatIllegalArgumentException()
            .isThrownBy { stackImageTags("auth-api=latest") }
    }

    @Test
    fun `rejects latest image refs`() {
        assertThatIllegalArgumentException()
            .isThrownBy { stackImageTags("ghcr.io/jorisjonkers-dev/auth-api:latest") }
    }

    @Test
    fun `rejects latest third party image refs`() {
        assertThatIllegalArgumentException()
            .isThrownBy { stackImageTags("ghcr.io/twin/gatus:latest") }
    }

    @Test
    fun `rejects unsupported services`() {
        assertThatIllegalArgumentException()
            .isThrownBy { stackImageTags("unknown-api=v1.0.0") }
    }
}

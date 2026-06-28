import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.jorisjonkers.kotlin)
    alias(libs.plugins.jorisjonkers.detekt)
    alias(libs.plugins.jorisjonkers.ktlint)
    alias(libs.plugins.jorisjonkers.test.logging)
}

dependencies {
    testImplementation("io.rest-assured:rest-assured:6.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("dev.turingcomplete:kotlin-onetimepassword:3.0.0")
    testImplementation("commons-codec:commons-codec:1.22.0")
    testRuntimeOnly("org.postgresql:postgresql:42.7.11")
    testImplementation("com.microsoft.playwright:playwright:1.60.0")
}

val testSourceSet = extensions.getByType(SourceSetContainer::class.java).getByName("test")
val imageTagsInput = providers.gradleProperty("imageTags").orElse(providers.environmentVariable("IMAGE_TAGS"))
val supportedImageTagServices =
    setOf(
        "auth-api",
        "auth-ui",
        "home-portal",
        "knowledge-api",
        "agents-api",
        "agents-ui",
        "agent-runtime",
    )

fun parseImageTags(raw: String): Map<String, String> {
    val entries = raw.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return entries.associate { entry ->
        val parts = entry.split("=", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw GradleException("Invalid IMAGE_TAGS entry: $entry")
        }
        val service = parts[0]
        val tag = parts[1]
        if (service !in supportedImageTagServices) {
            throw GradleException("Unsupported IMAGE_TAGS service: $service")
        }
        if (tag == "latest" || tag.endsWith(":latest")) {
            throw GradleException("IMAGE_TAGS entry for $service must not use latest")
        }
        service to tag
    }
}

fun Test.forwardCommonSystemProperties() {
    gradle.startParameter.systemPropertiesArgs
        .filterKeys { it.startsWith("test.") }
        .forEach(::systemProperty)

    imageTagsInput.orNull?.takeIf { it.isNotBlank() }?.let { raw ->
        systemProperty("test.image-tags", raw)
        parseImageTags(raw).forEach { (service, tag) ->
            systemProperty("test.image.$service.tag", tag)
        }
    }
}

fun Test.configureStructuralTestTask() {
    useJUnitPlatform {
        excludeTags("system")
    }
    forwardCommonSystemProperties()
}

fun Test.configureSystemTestTask() {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeTags("system")
    }
    forwardCommonSystemProperties()
    outputs.upToDateWhen { false }
}

tasks.test {
    configureStructuralTestTask()
}

tasks.register<Test>("testNonPlaywright") {
    group = "verification"
    description = "Runs non-Playwright system tests."
    configureSystemTestTask()
    filter {
        excludeTestsMatching("com.jorisjonkers.personalstack.systemtests.playwright.*")
    }
}

tasks.register<Test>("testPlaywright") {
    group = "verification"
    description = "Runs Playwright-based system tests."
    configureSystemTestTask()
    filter {
        includeTestsMatching("com.jorisjonkers.personalstack.systemtests.playwright.*")
    }
}

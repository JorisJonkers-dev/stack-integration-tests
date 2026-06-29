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
val hasImageTagsInput = imageTagsInput.map { it.isNotBlank() }.orElse(false)
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
    return entries
        .mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            val explicitService = parts.size == 2
            val service = if (explicitService) parts[0] else serviceFromImageRef(entry)
            val tag = if (explicitService) parts[1] else entry
            if (service.isBlank() || tag.isBlank()) {
                throw GradleException("Invalid IMAGE_TAGS entry: $entry")
            }
            if (!explicitService && !hasExplicitImageVersion(tag)) {
                throw GradleException("IMAGE_TAGS entry must use an explicit image tag or digest: $tag")
            }
            if (!explicitService && isLatestImageRef(tag)) {
                throw GradleException("IMAGE_TAGS entry must not use latest: $tag")
            }
            if (service !in supportedImageTagServices) {
                if (explicitService) {
                    throw GradleException("Unsupported IMAGE_TAGS service: $service")
                }
                return@mapNotNull null
            }
            if (isLatestImageRef(tag)) {
                throw GradleException("IMAGE_TAGS entry for $service must not use latest")
            }
            service to tag
        }.toMap()
}

fun serviceFromImageRef(ref: String): String {
    val imageName = ref.substringBefore("@").substringAfterLast("/").substringBefore(":")
    return when (imageName) {
        "knowledge" -> "knowledge-api"
        else -> imageName
    }
}

fun hasExplicitImageVersion(ref: String): Boolean {
    val imagePath = ref.substringBefore("@").substringAfterLast("/")
    return ref.contains("@sha256:") || imagePath.contains(":")
}

fun isLatestImageRef(ref: String): Boolean {
    val imagePath = ref.substringBefore("@").substringAfterLast("/")
    val imageTag = imagePath.substringAfter(":", missingDelimiterValue = "")
    return ref == "latest" || imagePath == "latest" || imageTag == "latest"
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
    onlyIf("IMAGE_TAGS is set") { hasImageTagsInput.get() }
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

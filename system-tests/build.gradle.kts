import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.jorisjonkers.kotlin)
    alias(libs.plugins.jorisjonkers.detekt)
    alias(libs.plugins.jorisjonkers.ktlint)
    alias(libs.plugins.jorisjonkers.test.logging)
}

dependencies {
    testImplementation(libs.kotlin.commons.system.test.harness)
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
val imageTagsInput =
    providers
        .gradleProperty("imageTags")
        .orElse(providers.systemProperty("test.image-tags"))
        .orElse(providers.environmentVariable("IMAGE_TAGS"))
val hasImageTagsInput = imageTagsInput.map { it.isNotBlank() }.orElse(false)

fun Test.forwardCommonSystemProperties() {
    gradle.startParameter.systemPropertiesArgs
        .filterKeys { it.startsWith("test.") }
        .forEach(::systemProperty)

    imageTagsInput.orNull?.takeIf { it.isNotBlank() }?.let { raw ->
        systemProperty("test.image-tags", raw)
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

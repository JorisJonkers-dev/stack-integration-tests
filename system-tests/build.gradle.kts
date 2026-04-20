plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("test-logging-conventions")
}

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

dependencies {
    testImplementation("io.rest-assured:rest-assured:6.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // Pinned to 1.21.x: Testcontainers 2.0 removed the junit-jupiter
    // submodule and restructured the base library. The jump isn't a
    // drop-in replacement; lift once we migrate system-tests to the
    // 2.x API.
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("dev.turingcomplete:kotlin-onetimepassword:2.4.1")
    testImplementation("commons-codec:commons-codec:1.21.0")
    testRuntimeOnly("org.postgresql:postgresql:42.7.10")
    testImplementation("com.microsoft.playwright:playwright:1.59.0")
}

val testSourceSet = extensions.getByType(SourceSetContainer::class.java).getByName("test")

fun Test.configureSystemTestTask() {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeTags("system")
    }
    gradle.startParameter.systemPropertiesArgs
        .filterKeys { it.startsWith("test.") }
        .forEach(::systemProperty)
    outputs.upToDateWhen { false }
}

tasks.test {
    configureSystemTestTask()
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

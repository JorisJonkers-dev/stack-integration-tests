plugins {
    alias(libs.plugins.extratoast.kotlin)
    alias(libs.plugins.extratoast.detekt)
    alias(libs.plugins.extratoast.ktlint)
    alias(libs.plugins.extratoast.test.logging)
}

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

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

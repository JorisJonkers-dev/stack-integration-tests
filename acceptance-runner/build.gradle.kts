plugins {
    application
    alias(libs.plugins.jorisjonkers.kotlin)
    alias(libs.plugins.jorisjonkers.detekt)
    alias(libs.plugins.jorisjonkers.ktlint)
    alias(libs.plugins.jorisjonkers.test.logging)
}

// The committed evaluation set lives in system-tests (fleet-infra#251 AC-1) and
// is not duplicated here: it is published onto this module's own classpath as
// a resource so the single committed copy stays the only source of truth.
sourceSets {
    main {
        resources.srcDir("../system-tests/src/test/resources")
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.yaml:snakeyaml:2.3")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass.set("com.jorisjonkers.personalstack.acceptance.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

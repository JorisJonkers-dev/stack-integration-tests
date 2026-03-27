plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
    id("test-logging-conventions")
}

dependencies {
    testImplementation("io.rest-assured:rest-assured:5.5.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}

tasks.test {
    useJUnitPlatform {
        includeTags("system")
    }
}

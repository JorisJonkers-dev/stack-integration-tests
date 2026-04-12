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
    testImplementation("dev.turingcomplete:kotlin-onetimepassword:2.4.0")
    testImplementation("commons-codec:commons-codec:1.17.1")
    testRuntimeOnly("org.postgresql:postgresql:42.7.6")
    testImplementation("com.microsoft.playwright:playwright:1.52.0")
}

tasks.test {
    useJUnitPlatform {
        includeTags("system")
    }
    outputs.upToDateWhen { false }
    maxHeapSize = "512m"
}

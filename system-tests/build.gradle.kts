plugins {
    id("kotlin-conventions")
    id("detekt-conventions")
    id("ktlint-conventions")
}

dependencies {
    testImplementation("io.rest-assured:rest-assured:5.5.1")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.test {
    useJUnitPlatform {
        includeTags("system")
    }
}

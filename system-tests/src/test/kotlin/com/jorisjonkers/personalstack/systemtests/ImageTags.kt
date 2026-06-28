package com.jorisjonkers.personalstack.systemtests

object ImageTags {
    val supportedServices: Set<String> =
        setOf(
            "auth-api",
            "auth-ui",
            "home-portal",
            "knowledge-api",
            "agents-api",
            "agents-ui",
            "agent-runtime",
        )

    fun fromSystemProperties(): ImageTagSet =
        parse(
            System.getProperty("test.image-tags", System.getenv("IMAGE_TAGS").orEmpty()),
        )

    fun parse(raw: String): ImageTagSet {
        if (raw.isBlank()) {
            return ImageTagSet(emptyMap())
        }

        val parsed =
            raw
                .trim()
                .split(Regex("\\s+"))
                .associate { entry ->
                    val parts = entry.split("=", limit = 2)
                    require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        "Invalid IMAGE_TAGS entry: $entry"
                    }
                    val service = parts[0]
                    val tag = parts[1]
                    require(service in supportedServices) { "Unsupported IMAGE_TAGS service: $service" }
                    require(tag != "latest" && !tag.endsWith(":latest")) {
                        "IMAGE_TAGS entry for $service must use an explicit non-latest tag"
                    }
                    service to tag
                }

        return ImageTagSet(parsed)
    }
}

data class ImageTagSet(
    val tags: Map<String, String>,
) {
    fun tagFor(service: String): String? = tags[service] ?: System.getProperty("test.image.$service.tag")
}

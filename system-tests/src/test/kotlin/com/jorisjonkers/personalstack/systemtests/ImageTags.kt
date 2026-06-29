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
    private val serviceAliases = mapOf("knowledge" to "knowledge-api")

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
                .mapNotNull { entry ->
                    val parts = entry.split("=", limit = 2)
                    val explicitService = parts.size == 2
                    val service = if (explicitService) parts[0] else serviceFromImageRef(entry)
                    val tag = if (explicitService) parts[1] else entry
                    require(service.isNotBlank() && tag.isNotBlank()) {
                        "Invalid IMAGE_TAGS entry: $entry"
                    }
                    require(explicitService || hasExplicitImageVersion(tag)) {
                        "IMAGE_TAGS entry must use an explicit image tag or digest: $tag"
                    }
                    require(explicitService || !isLatestImageRef(tag)) {
                        "IMAGE_TAGS entry must use an explicit non-latest tag: $tag"
                    }
                    if (service !in supportedServices) {
                        require(!explicitService) { "Unsupported IMAGE_TAGS service: $service" }
                        return@mapNotNull null
                    }
                    require(!isLatestImageRef(tag)) {
                        "IMAGE_TAGS entry for $service must use an explicit non-latest tag"
                    }
                    service to tag
                }.toMap()

        return ImageTagSet(parsed)
    }

    private fun serviceFromImageRef(ref: String): String {
        val imageName = ref.substringBefore("@").substringAfterLast("/").substringBefore(":")
        return serviceAliases[imageName] ?: imageName
    }

    private fun hasExplicitImageVersion(ref: String): Boolean {
        val imagePath = ref.substringBefore("@").substringAfterLast("/")
        return ref.contains("@sha256:") || imagePath.contains(":")
    }

    private fun isLatestImageRef(ref: String): Boolean {
        val imagePath = ref.substringBefore("@").substringAfterLast("/")
        val imageTag = imagePath.substringAfter(":", missingDelimiterValue = "")
        return ref == "latest" || imagePath == "latest" || imageTag == "latest"
    }
}

data class ImageTagSet(
    val tags: Map<String, String>,
) {
    fun tagFor(service: String): String? = tags[service] ?: System.getProperty("test.image.$service.tag")
}

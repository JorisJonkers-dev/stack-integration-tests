package com.jorisjonkers.personalstack.systemtests

import com.jorisjonkers.personalstack.common.test.system.ImageTags

val stackImageServices: Set<String> =
    setOf(
        "auth-api",
        "auth-ui",
        "home-portal",
        "knowledge-api",
        "agents-api",
        "agents-ui",
        "agent-runtime",
    )

val stackImageAliases: Map<String, String> = mapOf("knowledge" to "knowledge-api")

fun stackImageTags(raw: String): ImageTags =
    ImageTags.parse(
        raw = raw,
        allowedServices = stackImageServices,
        serviceAliases = stackImageAliases,
    )

fun stackImageTagsFromEnvironment(): ImageTags =
    ImageTags.fromEnvironment(
        allowedServices = stackImageServices,
        serviceAliases = stackImageAliases,
    )

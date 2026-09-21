package com.jorisjonkers.personalstack.acceptance

/** Thrown when the runner refuses to touch a target because it looks like production. */
class ProductionTargetGuardError(
    message: String,
) : Exception(message)

package com.jorisjonkers.personalstack.acceptance

/**
 * Everything one target run seeded, so it can all be deleted again — the
 * runner must clean up what it seeded, whether or not the run itself
 * succeeded.
 */
class SeedTracker {
    private val refs = mutableListOf<String>()

    fun record(ref: String) {
        refs.add(ref)
    }

    fun all(): List<String> = refs.toList()
}

data class CleanupOutcome(
    val attempted: Int,
    val failed: List<CleanupFailure>,
)

data class CleanupFailure(
    val ref: String,
    val error: String,
)

/** Deletes every tracked ref, collecting failures instead of throwing — cleanup must not skip entries. */
fun cleanUp(
    tracker: SeedTracker,
    delete: (String) -> Unit,
): CleanupOutcome {
    val refs = tracker.all()
    val failures =
        refs.mapNotNull { ref ->
            try {
                delete(ref)
                null
            } catch (ex: TargetHttpError) {
                CleanupFailure(ref, ex.message ?: "cleanup failed")
            } catch (ex: McpToolError) {
                CleanupFailure(ref, ex.message ?: "cleanup failed")
            }
        }
    return CleanupOutcome(attempted = refs.size, failed = failures)
}

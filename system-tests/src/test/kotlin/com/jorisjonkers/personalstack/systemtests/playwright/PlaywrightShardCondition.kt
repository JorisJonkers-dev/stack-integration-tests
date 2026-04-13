package com.jorisjonkers.personalstack.systemtests.playwright

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext

private const val SHARD_INDEX_PROPERTY = "test.shard.index"
private const val SHARD_COUNT_PROPERTY = "test.shard.count"

class PlaywrightShardCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val testClass = context.testClass.orElse(null) ?: return ConditionEvaluationResult.enabled("No test class")
        val shard = PlaywrightShard.fromSystemProperties()
            ?: return ConditionEvaluationResult.enabled("Playwright sharding disabled")

        val assignedShard = Math.floorMod(testClass.name.hashCode(), shard.count) + 1
        return if (assignedShard == shard.index) {
            ConditionEvaluationResult.enabled("Class assigned to shard ${shard.index}/${shard.count}")
        } else {
            ConditionEvaluationResult.disabled(
                "Class assigned to shard ${assignedShard}/${shard.count}, current shard is ${shard.index}/${shard.count}",
            )
        }
    }
}

private data class PlaywrightShard(
    val index: Int,
    val count: Int,
) {
    companion object {
        fun fromSystemProperties(): PlaywrightShard? {
            val indexValue = System.getProperty(SHARD_INDEX_PROPERTY)?.trim()
            val countValue = System.getProperty(SHARD_COUNT_PROPERTY)?.trim()

            if (indexValue.isNullOrEmpty() && countValue.isNullOrEmpty()) {
                return null
            }

            require(!indexValue.isNullOrEmpty() && !countValue.isNullOrEmpty()) {
                "Both $SHARD_INDEX_PROPERTY and $SHARD_COUNT_PROPERTY must be set together"
            }

            val index = indexValue.toIntOrNull()
                ?: error("$SHARD_INDEX_PROPERTY must be an integer, got '$indexValue'")
            val count = countValue.toIntOrNull()
                ?: error("$SHARD_COUNT_PROPERTY must be an integer, got '$countValue'")

            require(count > 0) { "$SHARD_COUNT_PROPERTY must be greater than 0" }
            require(index in 1..count) {
                "$SHARD_INDEX_PROPERTY must be between 1 and $count, got $index"
            }

            return PlaywrightShard(index = index, count = count)
        }
    }
}

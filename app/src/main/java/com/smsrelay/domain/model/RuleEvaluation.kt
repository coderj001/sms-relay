package com.smsrelay.domain.model

sealed interface RuleEvaluation {
    data class Matched(val match: RuleMatch) : RuleEvaluation
    data object SenderMismatch : RuleEvaluation
    data object MessageMismatch : RuleEvaluation
    data class InvalidPattern(val message: String) : RuleEvaluation
}

data class RuleMatch(
    val value: String,
    val groups: List<String?>,
    val namedGroups: Map<String, String?> = emptyMap(),
)


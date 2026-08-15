package com.smsrelay.domain.model

data class ExecutionLog(
    val id: Long,
    val ruleId: Long?,
    val receivedAt: Long,
    val senderPreview: String?,
    val destinationMasked: String?,
    val matchStatus: String,
    val sendStatus: String,
    val failureCode: SendFailureCode?,
    val createdAt: Long,
)


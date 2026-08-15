package com.smsrelay.domain.model

data class SmsRule(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val senderFilter: String?,
    val messageRegex: String,
    val destinationNumber: String,
    val outputTemplate: String,
    val createdAt: Long,
    val updatedAt: Long,
)


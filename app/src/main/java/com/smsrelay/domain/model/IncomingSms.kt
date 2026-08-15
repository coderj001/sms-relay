package com.smsrelay.domain.model

data class IncomingSms(
    val sender: String?,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int?,
)


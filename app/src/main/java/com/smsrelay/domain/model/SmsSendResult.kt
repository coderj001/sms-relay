package com.smsrelay.domain.model

sealed interface SmsSendResult {
    data object Requested : SmsSendResult
    data object Sent : SmsSendResult
    data object Delivered : SmsSendResult
    data class Failed(val code: SendFailureCode) : SmsSendResult
}

enum class SendFailureCode {
    RECEIVE_PERMISSION_MISSING,
    SEND_PERMISSION_MISSING,
    INVALID_DESTINATION,
    NO_ACTIVE_SUBSCRIPTION,
    NO_SERVICE,
    RADIO_OFF,
    TEMPLATE_ERROR,
    SEND_FAILED,
}


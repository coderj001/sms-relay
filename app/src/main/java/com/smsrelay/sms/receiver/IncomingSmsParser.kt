package com.smsrelay.sms.receiver

import android.content.Intent
import android.provider.Telephony
import com.smsrelay.domain.model.IncomingSms

object IncomingSmsParser {
    fun parse(intent: Intent): IncomingSms? {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return null
        val sender = messages.firstOrNull()?.originatingAddress
        val receivedAt = messages.maxOf { it.timestampMillis }
        return IncomingSms(sender, messages.joinToString(separator = "") { it.messageBody.orEmpty() }, receivedAt, messages.firstOrNull()?.subscriptionId)
    }
}

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
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }

        // Extract subscriptionId from intent extras as SmsMessage.subscriptionId is not available on all SDKs
        val subscriptionId = if (intent.hasExtra("subscription")) {
            intent.getIntExtra("subscription", -1)
        } else {
            intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
        }.takeIf { it != -1 }

        return IncomingSms(sender, body, receivedAt, subscriptionId)
    }
}

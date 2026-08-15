package com.smsrelay.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Platform entry point only. Processing is deliberately not connected until the
 * persistent rule repository and safety controls are implemented.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        // Keep the core invariant intact: no configured, enabled rule means no send.
    }
}


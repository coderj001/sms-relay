package com.smsrelay.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.smsrelay.domain.model.IncomingSms
import kotlinx.coroutines.launch

class DebugSendProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val body = intent.getStringExtra("body") ?: return
        val sms = IncomingSms(
            sender = intent.getStringExtra("sender") ?: "VM-KOTAKB-S",
            body = body,
            receivedAt = System.currentTimeMillis(),
            subscriptionId = if (intent.hasExtra("subscription")) intent.getIntExtra("subscription", -1).takeIf { it != -1 } else null,
        )
        val pendingResult = goAsync()
        CoroutineHolder.scope.launch {
            try {
                android.util.Log.i(TAG, "probe start sub=${sms.subscriptionId} dest=${intent.getStringExtra("destination")}")
                ProcessIncomingSmsUseCase(context.applicationContext).process(sms)
                android.util.Log.i(TAG, "probe end")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private object CoroutineHolder {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    }

    companion object {
        const val ACTION = "com.smsrelay.DEBUG_PROBE_SEND"
        const val TAG = "DEBUG-sms1f"
    }
}

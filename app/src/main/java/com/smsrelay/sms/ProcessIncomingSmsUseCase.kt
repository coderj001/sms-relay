package com.smsrelay.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smsrelay.data.ExecutionLogEntity
import com.smsrelay.data.SmsRelayDatabaseProvider
import com.smsrelay.data.SmsRuleEntity
import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.rule.RuleMatcher
import com.smsrelay.domain.template.TemplateRenderer
import com.smsrelay.domain.template.TemplateResult
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private val Context.settingsDataStore by preferencesDataStore("settings")

class ProcessIncomingSmsUseCase(private val context: Context) {
    private val dao = SmsRelayDatabaseProvider.get(context).dao()
    private val matcher = RuleMatcher()
    private val renderer = TemplateRenderer()

    suspend fun process(sms: IncomingSms) {
        val masterEnabled = context.settingsDataStore.data.first()[MASTER_AUTOMATION] ?: true
        if (!masterEnabled) return
        val rules = dao.enabledRules()
        for (rule in rules) processRule(rule, sms)
    }

    private suspend fun processRule(rule: SmsRuleEntity, sms: IncomingSms) {
        val domainRule = rule.toDomain()
        val evaluation = matcher.evaluate(domainRule, sms)
        val match = evaluation as? com.smsrelay.domain.model.RuleEvaluation.Matched ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            log(sms, rule, "PERMISSION_MISSING", "SEND_SMS permission is not granted")
            return
        }
        if (!PhoneNumberUtils.isGlobalPhoneNumber(rule.destinationNumber)) {
            log(sms, rule, "FAILED", "Invalid destination number")
            return
        }
        if (dao.countSince(System.currentTimeMillis() - 60_000L) >= 5) {
            log(sms, rule, "RATE_LIMITED", "Automatic send limit reached")
            return
        }
        val rendered = renderer.render(rule.outputTemplate, sms, match.match)
        val message = (rendered as? TemplateResult.Success)?.value ?: run {
            log(sms, rule, "FAILED", "Template contains an unknown variable")
            return
        }
        val fingerprint = fingerprint(sms, rule.id)
        if (log(sms, rule, "SEND_REQUESTED", null, fingerprint) == -1L) return
        try {
            val manager = sms.subscriptionId?.let { SmsManager.getSmsManagerForSubscriptionId(it) } ?: SmsManager.getDefault()
            manager.sendTextMessage(rule.destinationNumber, null, message, null, null)
            log(sms, rule, "SENT", null, fingerprint + ":sent")
        } catch (exception: Exception) {
            log(sms, rule, "FAILED", exception.message ?: "SMS send failed", fingerprint + ":failed")
        }
    }

    private suspend fun log(sms: IncomingSms, rule: SmsRuleEntity, status: String, detail: String?, fingerprint: String = fingerprint(sms, rule.id)): Long =
        dao.insertExecutionLog(ExecutionLogEntity(0, fingerprint, rule.id, sms.receivedAt, sms.sender?.takeLast(4), mask(rule.destinationNumber), status, detail, System.currentTimeMillis()))

    private fun fingerprint(sms: IncomingSms, ruleId: Long): String = sha256("$ruleId|${sms.sender}|${sms.body}|${sms.receivedAt / 60_000}")

    private fun mask(value: String): String = if (value.length > 4) "••••••${value.takeLast(4)}" else value

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun SmsRuleEntity.toDomain() = com.smsrelay.domain.model.SmsRule(id, name, enabled, senderFilter, messageRegex, destinationNumber, outputTemplate, createdAt, updatedAt)

    companion object {
        val MASTER_AUTOMATION = booleanPreferencesKey("master_automation")
    }
}

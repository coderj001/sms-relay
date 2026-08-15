package com.smsrelay.domain.rule

import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleEvaluation
import com.smsrelay.domain.model.RuleMatch
import com.smsrelay.domain.model.SmsRule

class RuleMatcher {
    fun evaluate(rule: SmsRule, sms: IncomingSms): RuleEvaluation {
        if (!senderMatches(rule.senderFilter, sms.sender)) return RuleEvaluation.SenderMismatch

        val regex = try {
            Regex(rule.messageRegex)
        } catch (exception: IllegalArgumentException) {
            return RuleEvaluation.InvalidPattern(exception.message.orEmpty())
        }
        val result = regex.find(sms.body) ?: return RuleEvaluation.MessageMismatch

        return RuleEvaluation.Matched(
            RuleMatch(
                value = result.value,
                groups = result.groups.drop(1).map { it?.value },
            ),
        )
    }

    private fun senderMatches(filter: String?, sender: String?): Boolean {
        val normalizedFilter = normalizeSender(filter)
        return normalizedFilter == null || normalizedFilter == normalizeSender(sender)
    }

    private fun normalizeSender(value: String?): String? =
        value?.trim()?.takeIf(String::isNotEmpty)?.uppercase()
}


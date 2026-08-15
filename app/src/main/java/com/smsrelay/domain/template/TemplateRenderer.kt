package com.smsrelay.domain.template

import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleMatch

class TemplateRenderer {
    fun render(template: String, sms: IncomingSms, match: RuleMatch): TemplateResult {
        val values = mapOf(
            "message" to sms.body,
            "sender" to sms.sender.orEmpty(),
            "match_0" to match.value,
            "match_1" to match.groups.getOrNull(0).orEmpty(),
            "match_2" to match.groups.getOrNull(1).orEmpty(),
            "timestamp" to sms.receivedAt.toString(),
        )
        val unknown = VARIABLE.findAll(template)
            .map { it.groupValues[1] }
            .firstOrNull { it !in values }
            ?: return TemplateResult.Success(
                VARIABLE.replace(template) { values.getValue(it.groupValues[1]) },
            )

        return TemplateResult.UnknownVariable(unknown)
    }

    private companion object {
        val VARIABLE = Regex("\\{\\{([a-zA-Z0-9_]+)}}")
    }
}

sealed interface TemplateResult {
    data class Success(val value: String) : TemplateResult
    data class UnknownVariable(val variable: String) : TemplateResult
}

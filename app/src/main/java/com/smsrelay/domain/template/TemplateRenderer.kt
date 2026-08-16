package com.smsrelay.domain.template

import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleMatch

class TemplateRenderer {
    fun render(template: String, sms: IncomingSms, match: RuleMatch): TemplateResult {
        val values = buildMap {
            put("message", sms.body)
            put("sender", sms.sender.orEmpty())
            put("match_0", match.value)
            put("timestamp", sms.receivedAt.toString())
            match.groups.forEachIndexed { index, value ->
                put("match_${index + 1}", value.orEmpty())
            }
        }
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

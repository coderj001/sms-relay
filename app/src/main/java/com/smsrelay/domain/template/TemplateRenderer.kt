package com.smsrelay.domain.template

import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleMatch

class TemplateRenderer {
    fun render(template: String, sms: IncomingSms, match: RuleMatch): TemplateResult {
        val values = buildMap {
            put("message", sms.body)
            put("sender", sms.sender.orEmpty())
            put("timestamp", sms.receivedAt.toString())
            put("match_0", match.value)
            match.groups.forEachIndexed { index, group -> put("match_" + (index + 1), group.orEmpty()) }
            match.namedGroups.forEach { (name, value) -> put(name, value.orEmpty()) }
        }
        val unknown = VARIABLE.findAll(template)
            .map { it.groupValues[1] }
            .firstOrNull { it !in values && !MATCH_INDEX.matches(it) }
            ?: return TemplateResult.Success(
                VARIABLE.replace(template) { values[it.groupValues[1]] ?: "" },
            )

        return TemplateResult.UnknownVariable(unknown)
    }

    private companion object {
        val VARIABLE = Regex("\\{\\{([a-zA-Z0-9_]+)\\}\\}")
        val MATCH_INDEX = Regex("^match_[0-9]+$")
    }
}

sealed interface TemplateResult {
    data class Success(val value: String) : TemplateResult
    data class UnknownVariable(val variable: String) : TemplateResult
}

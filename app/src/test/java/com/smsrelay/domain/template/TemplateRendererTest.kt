package com.smsrelay.domain.template

import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateRendererTest {
    @Test
    fun `renders supported variables and missing groups as empty`() {
        val result = TemplateRenderer().render(
            "From {{sender}}: {{match_1}}/{{match_2}}",
            IncomingSms("BANK", "credit 500", 0, null),
            RuleMatch("credit 500", listOf("500")),
        )

        assertEquals(TemplateResult.Success("From BANK: 500/"), result)
    }
}

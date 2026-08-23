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

    @Test
    fun `renders named groups from the match`() {
        val result = TemplateRenderer().render(
            "{{sender}} paid {{price}} ref {{reference}}",
            IncomingSms("VM-KOTAKB-S", "paid 500 ref ab12", 0, null),
            RuleMatch("paid 500 ref ab12", emptyList(), mapOf("price" to "500", "reference" to "ab12")),
        )

        assertEquals(TemplateResult.Success("VM-KOTAKB-S paid 500 ref ab12"), result)
    }

    @Test
    fun `unmatched optional named group renders empty`() {
        val result = TemplateRenderer().render(
            "a{{missing}}b",
            IncomingSms("BANK", "x", 0, null),
            RuleMatch("x", emptyList(), mapOf("missing" to null)),
        )

        assertEquals(TemplateResult.Success("ab"), result)
    }

    @Test
    fun `renders numbered groups beyond two`() {
        val result = TemplateRenderer().render(
            "{{match_1}}|{{match_2}}|{{match_3}}",
            IncomingSms("BANK", "a-b-c", 0, null),
            RuleMatch("a-b-c", listOf("a", "b", "c")),
        )

        assertEquals(TemplateResult.Success("a|b|c"), result)
    }

    @Test
    fun `unknown variable still fails loudly`() {
        val result = TemplateRenderer().render(
            "{{nope}}",
            IncomingSms("BANK", "x", 0, null),
            RuleMatch("x", emptyList()),
        )

        assertEquals(TemplateResult.UnknownVariable("nope"), result)
    }

    @Test
    fun `numbered group beyond registered range renders empty`() {
        val result = TemplateRenderer().render(
            "{{match_9}}!",
            IncomingSms("BANK", "x", 0, null),
            RuleMatch("x", listOf("a")),
        )

        assertEquals(TemplateResult.Success("!"), result)
    }

    @Test
    fun `renders on plain jvm regex semantics unchanged`() {
        val result = TemplateRenderer().render("{{sender}} {{timestamp}} {{match_0}}", IncomingSms(null, "body", 42, null), RuleMatch("body", emptyList()))
        assertEquals(TemplateResult.Success(" 42 body"), result)
    }
}

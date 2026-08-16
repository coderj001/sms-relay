package com.smsrelay.domain.rule

import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleEvaluation
import com.smsrelay.domain.model.SmsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    private val matcher = RuleMatcher()

    @Test
    fun `matches exact normalized sender and captures groups`() {
        val evaluation = matcher.evaluate(rule(sender = " bank ", regex = "INR\\s*(\\d+)"), sms())

        assertTrue(evaluation is RuleEvaluation.Matched)
        evaluation as RuleEvaluation.Matched
        assertEquals("INR 500", evaluation.match.value)
        assertEquals(listOf("500"), evaluation.match.groups)
    }

    @Test
    fun `sender mismatch does not evaluate as a match`() {
        assertEquals(RuleEvaluation.SenderMismatch, matcher.evaluate(rule(sender = "BANK"), sms(sender = "OTHER")))
    }

    @Test
    fun `invalid regex is reported`() {
        assertTrue(matcher.evaluate(rule(regex = "["), sms()) is RuleEvaluation.InvalidPattern)
    }

    @Test
    fun `supports a custom regex with named and optional groups`() {
        val evaluation = matcher.evaluate(
            rule(regex = "(?i)ref-(?<reference>[A-Z]{2}\\d{4})(?:\\s+INR\\s*(\\d+(?:\\.\\d{2})?))?"),
            sms().copy(body = "Payment REF-ab1234 INR 500.00"),
        )

        assertTrue(evaluation is RuleEvaluation.Matched)
        evaluation as RuleEvaluation.Matched
        assertEquals(listOf("ab1234", "500.00"), evaluation.match.groups)
    }

    private fun rule(sender: String? = null, regex: String = ".*") = SmsRule(
        id = 1,
        name = "Credit relay",
        enabled = false,
        senderFilter = sender,
        messageRegex = regex,
        destinationNumber = "+911234567890",
        outputTemplate = "{{message}}",
        createdAt = 0,
        updatedAt = 0,
    )

    private fun sms(sender: String = "BANK") = IncomingSms(sender, "Your account was credited INR 500", 0, null)
}

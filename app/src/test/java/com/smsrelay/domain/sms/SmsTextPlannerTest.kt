package com.smsrelay.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsTextPlannerTest {
    @Test
    fun `bengali message over 70 chars plans multipart ucs2 segments of 67`() {
        val message = "৳".repeat(84)

        val plan = SmsTextPlanner().plan(message)

        assertEquals(SmsEncoding.UCS_2, plan.encoding)
        assertEquals(listOf("৳".repeat(67), "৳".repeat(17)), plan.parts)
    }

    @Test
    fun `ucs2 message at exactly 70 chars plans single`() {
        val plan = SmsTextPlanner().plan("অ".repeat(70))

        assertEquals(SmsEncoding.UCS_2, plan.encoding)
        assertEquals(listOf("অ".repeat(70)), plan.parts)
    }

    @Test
    fun `gsm7 message within 160 chars plans single`() {
        val plan = SmsTextPlanner().plan("a".repeat(160))

        assertEquals(SmsEncoding.GSM_7BIT, plan.encoding)
        assertEquals(listOf("a".repeat(160)), plan.parts)
    }

    @Test
    fun `gsm7 message over 160 chars splits into 153-char segments`() {
        val plan = SmsTextPlanner().plan("a".repeat(200))

        assertEquals(listOf("a".repeat(153), "a".repeat(47)), plan.parts)
    }

    @Test
    fun `extended gsm characters count twice toward limits`() {
        assertEquals(
            listOf("|".repeat(80)),
            SmsTextPlanner().plan("|".repeat(80)).parts,
        )

        val overflow = SmsTextPlanner().plan("|".repeat(81))
        assertEquals(listOf("|".repeat(76), "|".repeat(5)), overflow.parts)
    }

    @Test
    fun `mixed gsm and non-gsm text falls back to ucs2`() {
        val plan = SmsTextPlanner().plan("Paid BDT 100 ✓done")

        assertEquals(SmsEncoding.UCS_2, plan.encoding)
    }

    @Test
    fun `empty message plans as single gsm part`() {
        val plan = SmsTextPlanner().plan("")

        assertEquals(listOf(""), plan.parts)
    }
}

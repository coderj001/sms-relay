package com.smsrelay.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `35 emoji fit in a single ucs2 message`() {
        val message = "😀".repeat(35)

        val plan = SmsTextPlanner().plan(message)

        assertTrue(plan is SmsTextPlan.Single)
        assertEquals(SmsEncoding.UCS_2, plan.encoding)
        assertEquals(listOf(message), plan.parts)
    }

    @Test
    fun `36 emoji require multipart ucs2 segments`() {
        val plan = SmsTextPlanner().plan("😀".repeat(36))

        assertTrue(plan is SmsTextPlan.Multipart)
        assertEquals(SmsEncoding.UCS_2, plan.encoding)
        assertEquals(listOf("😀".repeat(33), "😀".repeat(3)), plan.parts)
    }

    @Test
    fun `surrogate pair moves to next segment when only one code unit remains`() {
        val message = "a".repeat(66) + "😀" + "a".repeat(3)

        val plan = SmsTextPlanner().plan(message)

        assertEquals(listOf("a".repeat(66), "😀aaa"), plan.parts)
    }

    @Test
    fun `ucs2 segment can use all 67 code units with mixed text`() {
        val firstPart = "😀" + "a".repeat(65)
        val secondPart = "😀aa"

        val plan = SmsTextPlanner().plan(firstPart + secondPart)

        assertEquals(listOf(firstPart, secondPart), plan.parts)
    }

    @Test
    fun `unicode segmentation preserves text and surrogate pairs across boundaries`() {
        val planner = SmsTextPlanner()
        for (count in 1..160) {
            val message = "a😀".repeat(count)

            val plan = planner.plan(message)

            assertEquals(message, plan.text)
            assertEquals(message, plan.parts.joinToString(""))
            val limit = if (message.length <= 70) 70 else 67
            plan.parts.forEach { part ->
                assertTrue(part.isNotEmpty())
                assertTrue(part.length <= limit)
                assertFalse(Character.isLowSurrogate(part.first()))
                assertFalse(Character.isHighSurrogate(part.last()))
            }
        }
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

package com.smsrelay.domain.sms

enum class SmsEncoding { GSM_7BIT, UCS_2 }

sealed interface SmsTextPlan {
    val text: String
    val encoding: SmsEncoding
    val parts: List<String>

    data class Single(
        override val text: String,
        override val encoding: SmsEncoding,
    ) : SmsTextPlan {
        override val parts: List<String> = listOf(text)
    }

    data class Multipart(
        override val text: String,
        override val encoding: SmsEncoding,
        override val parts: List<String>,
    ) : SmsTextPlan
}

class SmsTextPlanner {
    fun plan(text: String): SmsTextPlan {
        val encoding = if (text.all { it.isGsm7Bit() }) SmsEncoding.GSM_7BIT else SmsEncoding.UCS_2
        return when (encoding) {
            SmsEncoding.GSM_7BIT -> planGsm7Bit(text)
            SmsEncoding.UCS_2 -> planUcs2(text)
        }
    }

    private fun planGsm7Bit(text: String): SmsTextPlan =
        if (text.sumOf { gsmSeptets(it) } <= SINGLE_GSM_SEPTETS) {
            SmsTextPlan.Single(text, SmsEncoding.GSM_7BIT)
        } else {
            SmsTextPlan.Multipart(text, SmsEncoding.GSM_7BIT, splitBySeptetWeight(text, MULTIPART_GSM_SEPTETS))
        }

    private fun planUcs2(text: String): SmsTextPlan {
        val codePoints = text.codePointCount(0, text.length)
        return if (codePoints <= SINGLE_UCS2_CODE_UNITS) {
            SmsTextPlan.Single(text, SmsEncoding.UCS_2)
        } else {
            SmsTextPlan.Multipart(text, SmsEncoding.UCS_2, splitByCodePoints(text, MULTIPART_UCS2_CODE_UNITS))
        }
    }

    private fun splitBySeptetWeight(text: String, limit: Int): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var weight = 0
        for (character in text) {
            val cost = gsmSeptets(character)
            if (weight + cost > limit && current.isNotEmpty()) {
                parts += current.toString()
                current.clear()
                weight = 0
            }
            current.append(character)
            weight += cost
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    private fun splitByCodePoints(text: String, limit: Int): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var count = 0
        var index = 0
        while (index < text.length) {
            if (count == limit) {
                parts += text.substring(start, index)
                start = index
                count = 0
            }
            index += Character.charCount(text.codePointAt(index))
            count++
        }
        if (start < text.length) parts += text.substring(start)
        return parts
    }

    private fun gsmSeptets(character: Char): Int =
        when {
            character in BASIC_ALPHABET -> 1
            character in EXTENSION_ALPHABET -> 2
            else -> throw IllegalArgumentException("Non-GSM character: U+%04X".format(character.code))
        }

    private fun Char.isGsm7Bit(): Boolean = this in BASIC_ALPHABET || this in EXTENSION_ALPHABET

    private companion object {
        const val SINGLE_GSM_SEPTETS = 160
        const val MULTIPART_GSM_SEPTETS = 153
        const val SINGLE_UCS2_CODE_UNITS = 70
        const val MULTIPART_UCS2_CODE_UNITS = 67

        val BASIC_ALPHABET = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        val EXTENSION_ALPHABET = "\u000C^{}\\[~]|€"
    }
}

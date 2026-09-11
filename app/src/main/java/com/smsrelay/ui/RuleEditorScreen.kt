@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsrelay.data.SmsRuleEntity
import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleEvaluation
import com.smsrelay.domain.model.SmsRule
import com.smsrelay.domain.rule.RuleMatcher
import com.smsrelay.domain.template.TemplateRenderer
import com.smsrelay.domain.template.TemplateResult
import java.util.regex.Pattern

internal data class RuleDraft(
    val name: String,
    val senderFilter: String?,
    val messageRegex: String,
    val destinationNumber: String,
    val outputTemplate: String,
    val enabled: Boolean,
)

@Composable
internal fun RuleEditorScreen(
    rule: SmsRuleEntity?,
    onBack: () -> Unit,
    onSave: (RuleDraft) -> Unit,
    onTest: (RuleDraft) -> Unit,
) {
    var name by remember(rule?.id) { mutableStateOf(rule?.name.orEmpty()) }
    var incomingNumber by remember(rule?.id) { mutableStateOf(rule?.senderFilter.orEmpty()) }
    var anyNumber by remember(rule?.id) { mutableStateOf(rule?.senderFilter == null) }
    var pattern by remember(rule?.id) { mutableStateOf(rule?.messageRegex.orEmpty()) }
    var destination by remember(rule?.id) { mutableStateOf(rule?.destinationNumber.orEmpty()) }
    var message by remember(rule?.id) { mutableStateOf(rule?.outputTemplate.orEmpty()) }
    var enabled by remember(rule?.id) { mutableStateOf(rule?.enabled ?: true) }
    var regexTested by remember { mutableStateOf(false) }
    val patternError = remember(pattern) { runCatching { Regex(pattern) }.exceptionOrNull()?.message }
    val sampleMatch = remember(pattern) { runCatching { Regex(pattern).find("OTP is 123456") }.getOrNull() }
    val broadRule = anyNumber && pattern.trim() == ".*"
    val preview = remember(pattern, message, anyNumber, incomingNumber) {
        val sample = IncomingSms("VM-KOTAKB-S", "OTP is 123456", System.currentTimeMillis(), null)
        val draftRule = SmsRule(0, "preview", true, if (anyNumber) null else incomingNumber.trim().takeIf(String::isNotEmpty), pattern, "", message, 0L, 0L)
        when (val evaluation = RuleMatcher().evaluate(draftRule, sample)) {
            is RuleEvaluation.Matched -> when (val rendered = TemplateRenderer().render(message, sample, evaluation.match)) {
                is TemplateResult.Success -> MessagePreview.Rendered(rendered.value)
                is TemplateResult.UnknownVariable -> MessagePreview.Invalid("Unknown variable {{${rendered.variable}}}")
            }
            RuleEvaluation.SenderMismatch -> MessagePreview.SenderMismatch
            RuleEvaluation.MessageMismatch -> MessagePreview.NoMatch
            is RuleEvaluation.InvalidPattern -> MessagePreview.Invalid(evaluation.message)
        }
    }

    val draft = RuleDraft(
        name = name,
        senderFilter = if (anyNumber) null else incomingNumber,
        messageRegex = pattern,
        destinationNumber = destination,
        outputTemplate = message,
        enabled = enabled,
    )
    val canSave = patternError == null && name.isNotBlank() && pattern.isNotBlank() && (anyNumber || incomingNumber.isNotBlank()) && destination.isNotBlank() && message.isNotBlank()

    Scaffold(topBar = { AppTopBar(if (rule == null) "Create SMS Rule" else "Edit SMS Rule", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowStep("1", "Receive SMS from")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rule name") },
                    supportingText = { Text("Use a short name to identify this relay rule.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = incomingNumber,
                    onValueChange = { incomingNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Incoming phone number") },
                    placeholder = { Text("Any number") },
                    enabled = !anyNumber,
                    supportingText = { Text("Exact sender or wildcard: * = any characters, ? = one. Example: *-KOTAKB-*. Empty = any sender.") },
                    singleLine = true,
                )
                FilterChip(selected = anyNumber, onClick = { anyNumber = !anyNumber }, label = { Text("Any number") })
                FlowArrow()
                FlowStep("2", "Match message with regex")
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it; regexTested = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message Regex  ·  Regex") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    isError = patternError != null,
                    supportingText = { Text(patternError?.let { "Invalid regular expression: $it" } ?: "The SMS will trigger this rule only when the message matches this pattern.") },
                )
                TextButton(onClick = { regexTested = true }, enabled = patternError == null) { Text("Test Regex") }
                if (regexTested) RegexResultCard(match = sampleMatch)
                FlowArrow()
                FlowStep("3", "Send SMS to")
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Forward to phone number") },
                    supportingText = { Text("When the incoming SMS matches, an SMS will automatically be sent to this number.") },
                    singleLine = true,
                )
                FlowArrow()
                FlowStep("4", "Outgoing message")
                OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text("Message to send") }, minLines = 4, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                VariablePicker(pattern = pattern, onInsert = { message += it })
                MessagePreviewCard(preview = preview)
                HorizontalDivider(Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable Rule", style = MaterialTheme.typography.titleMedium)
                        Text("Enabled rules can automatically send SMS messages.", style = MaterialTheme.typography.bodySmall)
                    }
                    AppSwitch(enabled, { enabled = it })
                }
                if (broadRule) WarningCard()
                Spacer(Modifier.height(12.dp))
            }
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack, modifier = Modifier.weight(0.8f)) { Text("Cancel") }
                    OutlinedButton(onClick = { onTest(draft) }, modifier = Modifier.weight(1f), enabled = patternError == null) { Text("Test Rule") }
                    Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f), enabled = canSave) { Text("Save Rule") }
                }
            }
        }
    }
}

@Composable
private fun FlowStep(step: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(step, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FlowArrow() {
    Text("↓", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun RegexResultCard(match: MatchResult?) {
    val matched = match != null
    Card(
        border = BorderStroke(1.dp, if (matched) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (matched) "[ PATTERN MATCHED ]" else "[ NO MATCH ]", style = MaterialTheme.typography.labelLarge, color = if (matched) Success else MaterialTheme.colorScheme.error)
            Text("Sample checked: OTP is 123456", style = MaterialTheme.typography.bodySmall)
            if (matched && match.groups.size > 1) {
                Text("Captured groups", style = MaterialTheme.typography.labelMedium)
                match.groups.drop(1).forEachIndexed { index, group ->
                    CodeText("match_" + (index + 1) + " = " + group?.value.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun ChipRow(variables: List<String>, onInsert: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        variables.forEach { variable -> AssistChip(onClick = { onInsert(variable) }, label = { Text(variable, fontFamily = FontFamily.Monospace) }) }
    }
}

@Composable
private fun VariablePicker(pattern: String, onInsert: (String) -> Unit) {
    val namedGroups = remember(pattern) { "\\(\\?<([a-zA-Z][a-zA-Z0-9_]*)>".toRegex().findAll(pattern).map { it.groupValues[1] }.distinct().toList() }
    val groupCount = remember(pattern) {
        runCatching { Pattern.compile(pattern).matcher("").groupCount() }.getOrElse {
            val positional = runCatching { "\\((?!\\?)".toRegex().findAll(pattern).count() }.getOrDefault(0)
            positional + namedGroups.size
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Message data", style = MaterialTheme.typography.labelLarge)
        Text("{{sender}} who sent it · {{message}} the original SMS · {{timestamp}} arrival time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(listOf("{{sender}}", "{{message}}", "{{timestamp}}"), onInsert)
        Text("Regex captures", style = MaterialTheme.typography.labelLarge)
        Text("{{match_0}} whole match · {{match_N}} group N · named groups appear here automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow((0..groupCount).map { "{{match_$it}}" }, onInsert)
        if (namedGroups.isNotEmpty()) ChipRow(namedGroups.map { "{{$it}}" }, onInsert)
    }
}

private sealed interface MessagePreview {
    data class Rendered(val text: String) : MessagePreview
    data object SenderMismatch : MessagePreview
    data object NoMatch : MessagePreview
    data class Invalid(val reason: String) : MessagePreview
}

@Composable
private fun MessagePreviewCard(preview: MessagePreview) {
    val (label, value, isError) = when (preview) {
        is MessagePreview.Rendered -> Triple("[ PREVIEW ]", preview.text, false)
        MessagePreview.SenderMismatch -> Triple("[ PREVIEW ]", "Sample sender VM-KOTAKB-S does not match this rule.", true)
        MessagePreview.NoMatch -> Triple("[ PREVIEW ]", "Sample message does not match this pattern.", true)
        is MessagePreview.Invalid -> Triple("[ PREVIEW ]", preview.reason, true)
    }
    Card(
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (isError) MaterialTheme.colorScheme.error else Success)
            Text("Sample: OTP is 123456 · from VM-KOTAKB-S", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WarningCard() {
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text("This rule may match almost every SMS", fontWeight = FontWeight.SemiBold)
            Text("Review the destination and pattern before enabling it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(value: String, color: Color) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color), modifier = Modifier.padding(top = 6.dp).wrapContentWidth()) {
        Text(value.uppercase(), color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

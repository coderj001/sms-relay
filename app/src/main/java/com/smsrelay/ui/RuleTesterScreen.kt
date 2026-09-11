@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleEvaluation
import com.smsrelay.domain.model.SmsRule
import com.smsrelay.domain.rule.RuleMatcher
import com.smsrelay.domain.template.TemplateRenderer
import com.smsrelay.domain.template.TemplateResult

@Composable
internal fun RuleTesterScreen(draft: RuleDraft?, onBack: () -> Unit) {
    val rule = draft ?: return
    var sender by remember(rule.senderFilter) { mutableStateOf(rule.senderFilter.orEmpty()) }
    var body by remember { mutableStateOf("OTP is 123456") }
    var tested by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Test Rule", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("[ TEST MODE ]  Testing never sends a real SMS.", Modifier.padding(14.dp), style = MaterialTheme.typography.labelMedium)
            }
            SectionTitle("Sample sender")
            OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), singleLine = true)
            SectionTitle("Sample SMS")
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), minLines = 4, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            Button(onClick = { tested = true }, modifier = Modifier.fillMaxWidth()) { Text("Run test") }
            if (tested) TestResultCard(rule = rule, sender = sender, message = body)
        }
    }
}

@Composable
private fun TestResultCard(rule: RuleDraft, sender: String, message: String) {
    val sample = remember(sender, message) { IncomingSms(sender.trim().takeIf(String::isNotEmpty), message, System.currentTimeMillis(), null) }
    val draftRule = SmsRule(0, "test", true, rule.senderFilter?.trim()?.takeIf(String::isNotEmpty), rule.messageRegex, "", rule.outputTemplate, 0L, 0L)
    val evaluation = remember(rule.senderFilter, rule.messageRegex, rule.outputTemplate, sender, message) { RuleMatcher().evaluate(draftRule, sample) }
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (evaluation) {
                is RuleEvaluation.Matched -> {
                    Text("RULE MATCHED", style = MaterialTheme.typography.headlineSmall, color = Success)
                    Text("Sender  ·  Matched")
                    Text("Regex  ·  Matched")
                    Text("Captured values", style = MaterialTheme.typography.labelLarge)
                    CodeText("match_0: ${evaluation.match.value}")
                    evaluation.match.groups.forEachIndexed { index, group -> CodeText("match_${index + 1}: ${group.orEmpty()}") }
                    evaluation.match.namedGroups.forEach { (name, value) -> CodeText("$name: ${value.orEmpty()}") }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Outgoing SMS preview", style = MaterialTheme.typography.titleMedium)
                    when (val rendered = TemplateRenderer().render(rule.outputTemplate, sample, evaluation.match)) {
                        is TemplateResult.Success -> CodeText(rendered.value)
                        is TemplateResult.UnknownVariable -> CodeText("Unknown variable {{${rendered.variable}}}")
                    }
                }
                RuleEvaluation.SenderMismatch -> {
                    Text("RULE DID NOT MATCH", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text("Sender condition: Did not match")
                    Text("Regex: Not evaluated")
                }
                RuleEvaluation.MessageMismatch -> {
                    Text("RULE DID NOT MATCH", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text("Sender condition: Matched")
                    Text("Regex: Did not match")
                }
                is RuleEvaluation.InvalidPattern -> {
                    Text("RULE DID NOT MATCH", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text("Invalid pattern: ${evaluation.message}")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) = Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

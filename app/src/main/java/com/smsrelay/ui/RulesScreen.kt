@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smsrelay.data.SmsRuleEntity

@Composable
internal fun RulesScreen(
    contentPadding: PaddingValues,
    rules: List<SmsRuleEntity>,
    onCreate: () -> Unit,
    onEdit: (SmsRuleEntity) -> Unit,
    onDelete: (SmsRuleEntity) -> Unit,
    onRuleEnabledChange: (SmsRuleEntity, Boolean) -> Unit,
    receiveAllowed: Boolean,
    sendAllowed: Boolean,
    onOpenPermissions: () -> Unit,
) {
    val permissionsReady = receiveAllowed && sendAllowed
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { AppTopBar(title = "SMS Rules") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add Rule") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!permissionsReady) {
                PermissionWarningCard(receiveAllowed, sendAllowed, onOpenPermissions)
                Spacer(Modifier.height(12.dp))
            }
            if (rules.isEmpty()) {
                Text("No rules yet. Add a rule to start relaying matching SMS messages.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rules.forEach { rule ->
                    RuleCard(rule, onRuleEnabledChange, onEdit, onDelete)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun PermissionWarningCard(receiveAllowed: Boolean, sendAllowed: Boolean, onOpenPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Permissions required", style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull("Receive SMS".takeIf { !receiveAllowed }, "Send SMS".takeIf { !sendAllowed }).joinToString(", ") + " not granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenPermissions) { Text("Fix") }
        }
    }
}

@Composable
private fun RuleCard(
    rule: SmsRuleEntity,
    onEnabledChange: (SmsRuleEntity, Boolean) -> Unit,
    onEdit: (SmsRuleEntity) -> Unit,
    onDelete: (SmsRuleEntity) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AppSwitch(checked = rule.enabled, onCheckedChange = { onEnabledChange(rule, it) })
                IconButton(onClick = { onEdit(rule) }) { Icon(Icons.Filled.Edit, "Edit rule") }
                IconButton(onClick = { onDelete(rule) }) { Icon(Icons.Filled.Delete, "Delete rule") }
            }
            RuleValue("Incoming", rule.senderFilter ?: "Any number")
            RuleValue("Pattern", rule.messageRegex, mono = true)
            RuleValue("Send to", rule.destinationNumber)
        }
    }
}

@Composable
private fun RuleValue(label: String, value: String, mono: Boolean = false) {
    Spacer(Modifier.height(10.dp))
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

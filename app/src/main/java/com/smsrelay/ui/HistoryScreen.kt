@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smsrelay.data.ExecutionLogWithRule

internal enum class HistoryFilter { ALL, SENT, FAILED, BLOCKED, REQUESTED, MATCHED, OTHER }

internal data class HistoryItem(
    val id: Long,
    val ruleId: Long?,
    val rule: String,
    val status: HistoryFilter,
    val statusCode: String,
    val sender: String,
    val destination: String,
    val receivedTime: String,
    val time: String,
    val group: String,
    val summary: String,
    val detail: String,
)

internal fun ExecutionLogWithRule.toHistoryItem(): HistoryItem {
    val zone = java.time.ZoneId.systemDefault()
    val created = java.time.Instant.ofEpochMilli(log.createdAt).atZone(zone)
    val received = java.time.Instant.ofEpochMilli(log.receivedAt).atZone(zone)
    val timestampFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm:ss a z")
    val today = java.time.LocalDate.now(zone)
    val group = when (created.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> created.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
    return HistoryItem(
        id = log.id,
        ruleId = log.ruleId,
        rule = ruleName ?: "Deleted or unavailable rule",
        status = when (log.status) {
            "SENT" -> HistoryFilter.SENT
            "FAILED" -> HistoryFilter.FAILED
            "RATE_LIMITED", "PERMISSION_MISSING" -> HistoryFilter.BLOCKED
            "SEND_REQUESTED" -> HistoryFilter.REQUESTED
            "MATCHED" -> HistoryFilter.MATCHED
            else -> HistoryFilter.OTHER
        },
        statusCode = log.status,
        sender = log.senderPreview?.takeIf(String::isNotBlank)?.let { "••••$it" } ?: "Unknown",
        destination = log.destinationMasked ?: "—",
        receivedTime = received.format(timestampFormat),
        time = created.format(timestampFormat),
        group = group,
        summary = when (log.status) {
            "SENT" -> "Handed to Android for sending. Delivery is not confirmed."
            "SEND_REQUESTED" -> "A send attempt was recorded. A separate event records its outcome."
            "FAILED" -> "The SMS could not be submitted for sending."
            "RATE_LIMITED" -> "Sending was blocked by the automatic send limit."
            "PERMISSION_MISSING" -> "Sending was blocked because SMS permission is missing."
            "MATCHED" -> "The incoming sender and message matched this rule."
            else -> "An execution event was recorded."
        },
        detail = log.detail.orEmpty(),
    )
}

@Composable
internal fun HistoryScreen(
    contentPadding: PaddingValues,
    automationEnabled: Boolean,
    permissionsReady: Boolean,
    items: List<HistoryItem>,
    onClearHistory: () -> Unit,
    onReviewPermissions: () -> Unit,
    onOpenDetails: (HistoryItem) -> Unit,
) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val visible = items.filter { item ->
        (filter == HistoryFilter.ALL || item.status == filter) &&
            (query.isBlank() || listOf(item.rule, item.sender, item.destination, item.statusCode, item.summary, item.detail, item.id.toString(), item.ruleId?.toString().orEmpty())
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            AppTopBar(
                title = "History",
                titleContent = if (searching) {
                    {
                        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search history") })
                    }
                } else null,
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search history") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "History options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { DropdownMenuItem(text = { Text("Clear History") }, onClick = { menuOpen = false; clearDialog = true }) }
                    }
                },
            )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!permissionsReady) CompactBanner("SMS permissions are incomplete", "Review Permissions", MaterialTheme.colorScheme.tertiary, onReviewPermissions)
            if (!automationEnabled) CompactBanner("Automation is currently off", null, MaterialTheme.colorScheme.onSurfaceVariant, {})
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { item -> FilterChip(filter == item, { filter = item }, label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
            if (visible.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (items.isEmpty()) "No activity yet" else "No matching activity", style = MaterialTheme.typography.titleMedium)
                    Text(if (items.isEmpty()) "When an SMS matches one of your rules, its execution result will appear here." else "Try a different search or status filter.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visible.groupBy { it.group }.forEach { (group, records) ->
                        Text(group, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        records.forEach { HistoryEntry(it, { onOpenDetails(it) }) }
                    }
                }
            }
        }
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Clear execution history?") }, text = { Text("This removes local history records. Your SMS rules will not be deleted.") }, confirmButton = { TextButton(onClick = { onClearHistory(); clearDialog = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
}

@Composable
private fun HistoryEntry(item: HistoryItem, onClick: () -> Unit) {
    val (color, label) = statusMeta(item.status)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.rule, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("[ ${label.uppercase()} ]", color = color, style = MaterialTheme.typography.labelMedium)
            Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            DetailRow("From", item.sender)
            DetailRow("To", item.destination)
            DetailRow("Received", item.receivedTime)
            DetailRow("Recorded", item.time)
            if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Event #${item.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("View details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CompactBanner(message: String, action: String?, color: Color, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Warning, null, tint = color); Spacer(Modifier.width(8.dp)); Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); if (action != null) TextButton(onClick = onAction) { Text(action) } }
}

@Composable
internal fun ExecutionDetailsScreen(item: HistoryItem?, onBack: () -> Unit) {
    if (item == null) { onBack(); return }
    val (color, label) = statusMeta(item.status)
    Scaffold(topBar = { AppTopBar("Execution Details", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, color), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("[ ${label.uppercase()} ]", color = color, style = MaterialTheme.typography.labelLarge)
                    Text(item.rule, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DetailSection("Incoming message") {
                DetailRow("From", item.sender)
                DetailRow("Received", item.receivedTime)
                Text("Only the sender's last four characters are retained. Message content is not stored in history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DetailSection("Outgoing SMS") {
                DetailRow("To", item.destination)
                DetailRow("Status", label)
                if (item.status == HistoryFilter.SENT) DetailRow("Delivery", "Not confirmed")
            }
            if (item.detail.isNotBlank()) {
                DetailSection(if (item.status == HistoryFilter.FAILED || item.status == HistoryFilter.BLOCKED) "Failure / blocking reason" else "Additional details") {
                    Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = color)
                }
            }
            DetailSection("Execution record") {
                DetailRow("Event ID", "#${item.id}")
                DetailRow("Rule ID", item.ruleId?.let { "#$it" } ?: "Unavailable")
                DetailRow("Recorded", item.time)
                DetailRow("Status code", item.statusCode)
            }
        }
    }
}

@Composable
private fun statusMeta(status: HistoryFilter): Pair<Color, String> = when (status) {
    HistoryFilter.SENT -> Success to "Send submitted"
    HistoryFilter.FAILED -> MaterialTheme.colorScheme.error to "Failed to send"
    HistoryFilter.BLOCKED -> MaterialTheme.colorScheme.tertiary to "Blocked"
    HistoryFilter.REQUESTED -> MaterialTheme.colorScheme.secondary to "Send requested"
    HistoryFilter.MATCHED -> MaterialTheme.colorScheme.secondary to "Rule matched"
    HistoryFilter.OTHER, HistoryFilter.ALL -> MaterialTheme.colorScheme.onSurfaceVariant to "Activity"
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } } }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

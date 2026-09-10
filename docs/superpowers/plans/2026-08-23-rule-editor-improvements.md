# Rule Editor & Matching Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wildcard sender matching (`*-KOTAKB-*`), a fixed + upgraded template engine (named-group variables, no Android regex crash), and a clearer editor Step 4 with dynamic variable chips and a live rendered preview.

**Architecture:** Two pure-Kotlin domain changes (RuleMatcher glob matching; RuleMatch/TemplateRenderer named groups) behind TDD, then one Compose change in the editor consuming them for chips and preview. The preview reuses the real RuleMatcher + TemplateRenderer against a sample SMS — no duplicated evaluation logic.

**Tech Stack:** Kotlin/JUnit (existing test suite), Jetpack Compose M3.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-23-rule-editor-improvements-design.md`.
- Glob semantics: `*` → any run of characters, `?` → exactly one character, all other characters literal after normalization (trim + uppercase both sides). No wildcard in filter = exact match. Blank filter = any sender.
- Template variables: `{{message}}`, `{{sender}}`, `{{timestamp}}`, `{{match_N}}` for EVERY numbered group (missing → empty), plus one variable per named group declared as `(?<name>…)` (unmatched optional → empty). Unknown variables still return `TemplateResult.UnknownVariable`.
- The template regex MUST NOT contain raw `\{` escapes (Android ICU PatternSyntaxException). Use `"\\{\\{([a-zA-Z0-9_]+)\\}\\}"` verbatim.
- `RuleMatch` gains `namedGroups: Map<String, String?>` **with default `emptyMap()`** so existing callers stay source-compatible.
- No comments added anywhere; follow each file's existing compact style.
- Build: `./gradlew` only (system gradle does not exist). Domain tests: `./gradlew :app:testDebugUnitTest --tests "..."`.

---

### Task 1: Wildcard sender matching in RuleMatcher

**Files:**
- Modify: `app/src/main/java/com/smsrelay/domain/rule/RuleMatcher.kt`
- Test: `app/src/test/java/com/smsrelay/domain/rule/RuleMatcherTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `RuleMatcher.evaluate(rule, sms)` now honors glob filters. Public signatures unchanged.

- [ ] **Step 1: Write failing tests**

Append inside `class RuleMatcherTest` (after the last test, before the private helpers):

```kotlin
    @Test
    fun `wildcard filter matches senders with varying prefix and suffix`() {
        val r = rule(sender = "*-KOTAKB-*")
        assertTrue(matcher.evaluate(r, sms(sender = "VM-KOTAKB-S")) is RuleEvaluation.Matched)
        assertTrue(matcher.evaluate(r, sms(sender = "AX-KOTAKB-P")) is RuleEvaluation.Matched)
    }

    @Test
    fun `wildcard filter rejects non-matching sender`() {
        assertEquals(RuleEvaluation.SenderMismatch, matcher.evaluate(rule(sender = "*-KOTAKB-*"), sms(sender = "VM-HDFCB-S")))
    }

    @Test
    fun `question mark matches exactly one character`() {
        assertTrue(matcher.evaluate(rule(sender = "BAN?"), sms(sender = "BANK")) is RuleEvaluation.Matched)
        assertEquals(RuleEvaluation.SenderMismatch, matcher.evaluate(rule(sender = "BAN?"), sms(sender = "BANKS")))
    }

    @Test
    fun `filter without wildcards stays exact match`() {
        assertTrue(matcher.evaluate(rule(sender = "*"), sms(sender = "ANYTHING")) is RuleEvaluation.Matched)
        assertEquals(RuleEvaluation.SenderMismatch, matcher.evaluate(rule(sender = "KOTAKB"), sms(sender = "VM-KOTAKB-S")))
    }

    @Test
    fun `wildcard filter with blank sender does not match`() {
        assertEquals(RuleEvaluation.SenderMismatch, matcher.evaluate(rule(sender = "*-KOTAKB-*"), sms(sender = "")))
    }
```

Note: `sms()` builds body "Your account was credited INR 500" with regex `.*`, so a sender match always evaluates as Matched here.

- [ ] **Step 2: Run tests, verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.smsrelay.domain.rule.RuleMatcherTest"`
Expected: FAIL — the wildcard tests fail because current matching is exact equality.

- [ ] **Step 3: Implement glob matching**

Replace the two private methods at the bottom of `RuleMatcher` (`senderMatches`, `normalizeSender`) with:

```kotlin
    private fun senderMatches(filter: String?, sender: String?): Boolean {
        val normalizedFilter = normalizeSender(filter) ?: return true
        val normalizedSender = normalizeSender(sender) ?: return false
        if (!normalizedFilter.contains('*') && !normalizedFilter.contains('?')) return normalizedFilter == normalizedSender
        return globToRegex(normalizedFilter).matches(normalizedSender)
    }

    private fun normalizeSender(value: String?): String? =
        value?.trim()?.takeIf(String::isNotEmpty)?.uppercase()

    private fun globToRegex(glob: String): Regex = Regex(
        buildString {
            glob.forEach { character ->
                when (character) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    else -> append(Regex.escape(character.toString()))
                }
            }
        },
    )
```

Behavior notes: blank filter returns true before touching sender (any sender); blank sender under a wildcard filter fails via the `?: return false`; `Regex.escape` makes literals like `-` safe.

- [ ] **Step 4: Run tests, verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.smsrelay.domain.rule.RuleMatcherTest"`
Expected: PASS, all tests green including the four pre-existing ones.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsrelay/domain/rule/RuleMatcher.kt app/src/test/java/com/smsrelay/domain/rule/RuleMatcherTest.kt
git commit -m "feat: wildcard sender matching in rule filters"
```

---

### Task 2: Named capture groups end-to-end (model, matcher, renderer)

**Files:**
- Modify: `app/src/main/java/com/smsrelay/domain/model/RuleEvaluation.kt` (the `RuleMatch` data class)
- Modify: `app/src/main/java/com/smsrelay/domain/rule/RuleMatcher.kt`
- Modify: `app/src/main/java/com/smsrelay/domain/template/TemplateRenderer.kt`
- Test: `app/src/test/java/com/smsrelay/domain/rule/RuleMatcherTest.kt`
- Test: `app/src/test/java/com/smsrelay/domain/template/TemplateRendererTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `RuleMatch(value: String, groups: List<String?>, namedGroups: Map<String, String?> = emptyMap())`. `TemplateRenderer.render(template, sms, match)` resolves `{{name}}` from `match.namedGroups` and `{{match_N}}` for all numbered groups.

- [ ] **Step 1: Write failing renderer tests**

In `TemplateRendererTest`, keep the existing test and add these imports at top (merge with existing):

```kotlin
import com.smsrelay.domain.model.RuleMatch
```

(this import already exists — skip it if so). Append inside the class:

```kotlin
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
```

Also add this regression test asserting rendering semantics are unchanged (the Android-crash itself cannot be reproduced on the JVM — it is an ICU-only failure; the crash fix is verified by code review of the regex form plus on-device logcat):

```kotlin
    @Test
    fun `renders on plain jvm regex semantics unchanged`() {
        val result = TemplateRenderer().render("{{sender}} {{timestamp}} {{match_0}}", IncomingSms(null, "body", 42, null), RuleMatch("body", emptyList()))
        assertEquals(TemplateResult.Success(" 42 body"), result)
    }
```

Use BOTH of: `renders on plain jvm regex semantics unchanged` and the four functional tests above. Drop the reflection-based one entirely.

- [ ] **Step 2: Write failing matcher test for named groups**

In `RuleMatcherTest`, append:

```kotlin
    @Test
    fun `populates named groups on match`() {
        val evaluation = matcher.evaluate(rule(regex = "(?<price>INR\\s*\\d+) total"), sms())

        assertTrue(evaluation is RuleEvaluation.Matched)
        evaluation as RuleEvaluation.Matched
        assertEquals(mapOf<String, String?>("price" to "INR 500"), evaluation.match.namedGroups)
    }

    @Test
    fun `unmatched optional named group maps to null`() {
        val evaluation = matcher.evaluate(rule(regex = "(?<code>[A-Z]{4})?(?:x)?INR"), sms().copy(body = "INR"))

        assertTrue(evaluation is RuleEvaluation.Matched)
        evaluation as RuleEvaluation.Matched
        assertEquals(mapOf<String, String?>("code" to null), evaluation.match.namedGroups)
    }
```

- [ ] **Step 3: Run tests, verify failures**

Run: `./gradlew :app:testDebugUnitTest --tests "com.smsrelay.domain.rule.RuleMatcherTest" --tests "com.smsrelay.domain.template.TemplateRendererTest"`
Expected: FAIL — `RuleMatch` has no `namedGroups` parameter (compile error in new tests) and named variables render as UnknownVariable.

- [ ] **Step 4: Extend the model**

In `app/src/main/java/com/smsrelay/domain/model/RuleEvaluation.kt`, replace the `RuleMatch` declaration with:

```kotlin
data class RuleMatch(
    val value: String,
    val groups: List<String?>,
    val namedGroups: Map<String, String?> = emptyMap(),
)
```

- [ ] **Step 5: Populate named groups in the matcher**

In `RuleMatcher.kt`: add import `kotlin.text.get` (top-level merge; needed for `MatchNamedGroupCollection.get(name)`), add companion regex, extend the Matched branch. The class becomes:

```kotlin
class RuleMatcher {
    fun evaluate(rule: SmsRule, sms: IncomingSms): RuleEvaluation {
        if (!senderMatches(rule.senderFilter, sms.sender)) return RuleEvaluation.SenderMismatch

        val regex = try {
            Regex(rule.messageRegex)
        } catch (exception: IllegalArgumentException) {
            return RuleEvaluation.InvalidPattern(exception.message.orEmpty())
        }
        val result = regex.find(sms.body) ?: return RuleEvaluation.MessageMismatch

        val namedGroups = GROUP_NAME.findAll(rule.messageRegex)
            .map { it.groupValues[1] }
            .associateWith { name -> (result.groups as? MatchNamedGroupCollection)?.get(name)?.value }

        return RuleEvaluation.Matched(
            RuleMatch(
                value = result.value,
                groups = result.groups.drop(1).map { it?.value },
                namedGroups = namedGroups,
            ),
        )
    }

    private fun senderMatches(filter: String?, sender: String?): Boolean {
        val normalizedFilter = normalizeSender(filter) ?: return true
        val normalizedSender = normalizeSender(sender) ?: return false
        if (!normalizedFilter.contains('*') && !normalizedFilter.contains('?')) return normalizedFilter == normalizedSender
        return globToRegex(normalizedFilter).matches(normalizedSender)
    }

    private fun normalizeSender(value: String?): String? =
        value?.trim()?.takeIf(String::isNotEmpty)?.uppercase()

    private fun globToRegex(glob: String): Regex = Regex(
        buildString {
            glob.forEach { character ->
                when (character) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    else -> append(Regex.escape(character.toString()))
                }
            }
        },
    )

    private companion object {
        val GROUP_NAME = Regex("\\(\\?<([a-zA-Z][a-zA-Z0-9_]*)>")
    }
}
```

(`MatchNamedGroupCollection` is `kotlin.text.MatchNamedGroupCollection` — auto-imported from the stdlib default imports; add an explicit `import kotlin.text.MatchNamedGroupCollection` only if the compiler asks.)

- [ ] **Step 6: Fix and upgrade the renderer**

Replace the entire content of `TemplateRenderer.kt` with:

```kotlin
package com.smsrelay.domain.template

import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleMatch

class TemplateRenderer {
    fun render(template: String, sms: IncomingSms, match: RuleMatch): TemplateResult {
        val values = buildMap {
            put("message", sms.body)
            put("sender", sms.sender.orEmpty())
            put("timestamp", sms.receivedAt.toString())
            put("match_0", match.value)
            match.groups.forEachIndexed { index, group -> put("match_" + (index + 1), group.orEmpty()) }
            match.namedGroups.forEach { (name, value) -> put(name, value.orEmpty()) }
        }
        val unknown = VARIABLE.findAll(template)
            .map { it.groupValues[1] }
            .firstOrNull { it !in values }
            ?: return TemplateResult.Success(
                VARIABLE.replace(template) { values.getValue(it.groupValues[1]) },
            )

        return TemplateResult.UnknownVariable(unknown)
    }

    private companion object {
        val VARIABLE = Regex("\\{\\{([a-zA-Z0-9_]+)\\}\\}")
    }
}

sealed interface TemplateResult {
    data class Success(val value: String) : TemplateResult
    data class UnknownVariable(val variable: String) : TemplateResult
}
```

The closing braces are escaped (`\\}\\}`) rather than the opening ones — Android ICU accepts this form; the old `\{\{` form throws PatternSyntaxException at class-init on device.

- [ ] **Step 7: Run tests, verify pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — full suite green (both domain test classes plus everything else).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/smsrelay/domain/model/RuleEvaluation.kt app/src/main/java/com/smsrelay/domain/rule/RuleMatcher.kt app/src/main/java/com/smsrelay/domain/template/TemplateRenderer.kt app/src/test/java/com/smsrelay/domain/rule/RuleMatcherTest.kt app/src/test/java/com/smsrelay/domain/template/TemplateRendererTest.kt
git commit -m "feat: named capture group template variables, fix android regex crash"
```

---

### Task 3: Editor UI — helper text, VariablePicker, live preview

**Files:**
- Modify: `app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt`

**Interfaces:**
- Consumes: `RuleMatcher.evaluate`, `TemplateRenderer.render`, `RuleEvaluation`/`RuleMatch`/`SmsRule`/`IncomingSms`/`TemplateResult` (all shipped by Tasks 1–2).
- Produces: private composables `VariablePicker(pattern: String, onInsert: (String) -> Unit)`, `ChipRow(variables: List<String>, onInsert: (String) -> Unit)`, `MessagePreviewCard(preview: MessagePreview)`; sealed interface `MessagePreview { Rendered(text); SenderMismatch; NoMatch; Invalid(reason) }`. `VariableChips` composable deleted.

- [ ] **Step 1: Add imports**

Merge into SmsRelayApp.kt's import block (skip any already present):

```kotlin
import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleEvaluation
import com.smsrelay.domain.model.SmsRule
import com.smsrelay.domain.rule.RuleMatcher
import com.smsrelay.domain.template.TemplateRenderer
import com.smsrelay.domain.template.TemplateResult
```

- [ ] **Step 2: Update Step 1 helper text**

In `RuleEditorScreen`, replace the incoming-number field's `supportingText` line:

```kotlin
                    supportingText = { Text("Only SMS messages received from this number will be checked.") },
```

with:

```kotlin
                    supportingText = { Text("Exact sender or wildcard: * = any characters, ? = one. Example: *-KOTAKB-*. Empty = any sender.") },
```

- [ ] **Step 3: Compute live preview state in RuleEditorScreen**

After the existing `val broadRule = ...` line (~369), insert:

```kotlin
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
```

- [ ] **Step 4: Replace VariableChips usage with VariablePicker + preview card**

Replace these two lines (~433–434):

```kotlin
                Text("Available variables", style = MaterialTheme.typography.labelLarge)
                VariableChips(onInsert = { message += it })
```

with:

```kotlin
                VariablePicker(pattern = pattern, onInsert = { message += it })
                MessagePreviewCard(preview = preview)
```

Delete the entire old `VariableChips` composable (lines ~492–497).

- [ ] **Step 5: Add the new composables**

Add below where `VariableChips` used to be:

```kotlin
@Composable
private fun ChipRow(variables: List<String>, onInsert: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        variables.forEach { variable -> AssistChip(onClick = { onInsert(variable) }, label = { Text(variable, fontFamily = FontFamily.Monospace) }) }
    }
}

@Composable
private fun VariablePicker(pattern: String, onInsert: (String) -> Unit) {
    val groupCount = remember(pattern) { runCatching { "\\((?!\\?)".toRegex().findAll(pattern).count() }.getOrDefault(0) }
    val namedGroups = remember(pattern) { "\\(\\?<([a-zA-Z][a-zA-Z0-9_]*)>".toRegex().findAll(pattern).map { it.groupValues[1] }.distinct().toList() }
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
```

Notes: `groupCount` counts unescaped capturing parens (approximation ignores `\(` literals — worst case an extra chip renders; unknown variables still error safely). `(0..groupCount)` yields `match_0` through `match_N`.

- [ ] **Step 6: Build and install**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, installed on 1 device. Fix unresolved imports first if flagged.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt
git commit -m "feat: editor variable picker with named-group chips and live preview"
```

---

### Task 4: On-device verification

**Files:** none (verification only)

**Interfaces:**
- Consumes: installed build from Task 3. Device over wireless ADB (id starts `adb-001236477008227`). Screenshots cannot be viewed in-session — verify exclusively via `adb shell uiautomator dump /sdcard/ui.xml` + grep text/bounds. Bottom nav: RULES (173,2232), HISTORY (540,2232), SETTINGS (907,2243). CAUTION: the device may be in active human use — re-dump before every tap and derive coordinates from the CURRENT dump, never from memory.

- [ ] **Step 1: Launch and open the editor**

Wake screen, launch app, tap RULES nav item, dump, find the Add Rule FAB (bottom-right clickable View above y≈2100) from fresh bounds, tap its center. Dump: expect "CREATE SMS RULE" title.

- [ ] **Step 2: Verify wildcard helper text**

Dump: expect the sender field supporting text mentioning wildcards and `*-KOTAKB-*`.

- [ ] **Step 3: Verify named-group chip appears dynamically**

Fill fields minimally (tap each field, `adb shell input text ...`): name `t`, destination `9`, message `x`. In the pattern field enter `(?<code>[A-Z]+)-(\d+)`. Dump Step 4 area: expect chips `{{match_0}}`, `{{match_1}}`, `{{match_2}}` AND a `{{code}}` chip.

- [ ] **Step 4: Verify preview success state**

Set message field to `got {{code}} g2={{match_2}}`. Dump: expect `[ PREVIEW ]` card showing rendered text `got SAMPLE g2=123456` — wait, sample body is `OTP is 123456`; expected render depends on what the pattern captures from that sample. Accept either the correctly rendered string per the entered pattern OR a `[ NO MATCH ]`/error state — then fix the pattern to `(?<code>OTP)` and expect preview text `got OTP`.

- [ ] **Step 5: Verify unknown-variable error**

Change message field to include `{{nope}}`. Dump: expect preview error naming `nope`.

- [ ] **Step 6: Cancel out, logcat sanity**

Press back/cancel (do NOT save the test rule). Run `adb logcat -d -t 200 | grep -iE "smsrelay.*(exception|crash)" | tail -5` — expect no NEW exceptions (pre-existing unrelated crashes documented in ledger).

- [ ] **Step 7: Record results**

Append outcome to `.superpowers/sdd/progress.md`. Commit nothing unless fixes were required.

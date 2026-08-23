# Rule Editor & Matching Improvements — Design

Date: 2026-08-23
Status: Approved (user confirmed)

## Problems

1. **Sender filter is exact-match only.** Real bank senders arrive as `VM-KOTAKB-S`,
   `AX-KOTAKB-P` — the operator prefix and message-type suffix vary per SMS, so a single
   exact value can never reliably match a bank. User wants `*-KOTAKB-*`.
2. **TemplateRenderer crashes on Android.** The variable regex `\{\{([a-zA-Z0-9_]+)}}`
   uses raw `\{` escapes that Android ICU rejects (`PatternSyntaxException`, observed in
   logcat). Every rule execution dies at render time.
3. **No named-group variables.** Patterns like `(?<price>…)` cannot be referenced;
   only `{{match_0}}..{{match_2}}` exist (hardcoded).
4. **Editor Step 4 variables are opaque** — a bare chip row of `{{sender}} {{message}}
   {{match_0}} {{match_1}}` with no explanations, no dynamic captures, no feedback of
   what the outgoing message will look like.

## Decisions (from user)

- Sender field gains **glob wildcards**: `*` (any run of characters) and `?` (exactly one
  character). No wildcard typed = exact match (current behavior preserved). Blank = any sender.
- Editor Step 4 gets **descriptions + auto chips + live preview**: labeled groups with
  descriptions, named-group chips generated live from the current pattern, and a rendered
  preview against a sample SMS.

## Behavior Spec

### 1. Wildcard sender matching (`RuleMatcher`)

- Normalization unchanged: trim + uppercase both sides before comparing.
- Filter null/blank → any sender (unchanged).
- Filter contains `*` or `?` → glob semantics: `*` → `.*`, `?` → `.`, every other char
  regex-escaped literal. Full-string match against normalized sender.
- Otherwise → exact equality (unchanged).
- Applies identically in the editor's inline "Test Regex" flow? No — sender matching is not
  part of Step 2's regex test; it applies at full-rule evaluation (tester screen, live runs,
  new preview).

### 2. Template engine (`TemplateRenderer`)

- Fix the Android-incompatible regex: escape closing braces instead of opening ones —
  `"\\{\\{([a-zA-Z0-9_]+)\\}\\}"`.
- Supported variables become:
  - `{{message}}`, `{{sender}}`, `{{timestamp}}` (unchanged)
  - `{{match_N}}` for **every** numbered capture group (removes the hardcoded 2-group cap);
    missing groups render empty (unchanged)
  - `{{name}}` for each **named group** declared in the rule's regex via `(?<name>…)`;
    unmatched optionals render empty
- Unknown variables still fail loudly: `TemplateResult.UnknownVariable` (unchanged safety).

### 3. Match model (`RuleMatch`)

- Gains `namedGroups: Map<String, String?>` (default `emptyMap()`) so existing callers/tests
  stay source-compatible. `RuleMatcher` populates it by scanning the pattern for group names
  (`\(\?<([a-zA-Z][a-zA-Z0-9_]*)>`) and resolving each via the platform's named-group access.

### 4. Editor UI (`RuleEditorScreen`)

- **Step 1** sender field helper text: explains wildcards — "Exact sender or wildcard: *
  matches any characters, ? matches one. Example: *-KOTAKB-*. Empty = any sender."
  (final copy may be tightened in implementation).
- **Step 4** replaces the flat chip row with a `VariablePicker`:
  - Group "Message data": `{{sender}} {{message}} {{timestamp}}` with one-line description
    ("Who sent it / full original SMS / arrival time").
  - Group "Regex captures": `{{match_0}}, {{match_1}}, … {{match_N}}` where N follows the
    current pattern's group count, plus one chip per named group parsed live from the current
    pattern (e.g. typing `(?<price>…)` in Step 2 makes `{{price}}` appear here).
  - Chips insert into the template at cursor-independent end-of-text (current behavior kept).
- **Live preview card** below the picker:
  - Evaluates the current draft (sender wildcard + regex + template) against a fixed sample
    SMS (sender `VM-KOTAKB-S`, body consistent with the existing "OTP is 123456" sample)
    using the real `RuleMatcher` + `TemplateRenderer`.
  - States: rendered final message text (success); "no match" hint; invalid-pattern error;
    unknown-variable error naming the bad variable.
  - Recomputes as the user types (derived state, no manual refresh).

### Out of scope

- Rule-list screen changes; richer matching DSL beyond `*`/`?`; cursor-position insertion;
  editing the tester screen's separate flow beyond what the model/renderer changes give it
  for free.

## Verification Plan

1. Unit tests (TDD, plain JUnit — these layers are pure Kotlin):
   - `RuleMatcherTest`: wildcard hit/miss cases (`*-KOTAKB-*` vs `VM-KOTAKB-S`,
     `AX-KOTAKB-P`, wrong bank; `J?TAKB`; exact behavior without wildcards; blank sender vs
     wildcard filter).
   - `TemplateRendererTest`: named groups render; optional named group unmatched → empty;
     numbered groups beyond 2; unknown variable still fails; regression test that rendering
     does not depend on the crashing regex form.
2. Build + install; device UI-dump checks: helper text present; typing a named group in
   Step 2 adds its chip in Step 4; preview renders with sample data and updates on edits.

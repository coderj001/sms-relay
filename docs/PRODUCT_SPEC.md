# Product Specification

## 1. Product Summary

SMS Rule Relay is an Android automation app. It watches new incoming SMS messages and evaluates user-created rules. When an enabled rule matches, the app renders a message template and sends an SMS to the destination configured in that rule.

Example:

```text
Incoming sender: AD-HDFCBK
Incoming body: Your account was credited INR 5,000.00
Rule regex: credited.*INR\s*([\d,.]+)
Destination: +91XXXXXXXXXX
Template: Payment received: INR {{match_1}}
```

Result:

```text
Payment received: INR 5,000.00
```

## 2. Problem

Important SMS alerts may need to reach another person or device automatically. Manual forwarding is slow and unreliable. Existing automation tools may be too broad, require cloud access, or do not provide deterministic regex matching.

The app should solve this with a local, understandable rule model.

## 3. Target Users

Primary users:

- Individuals forwarding selected alerts to another phone
- Small teams using dedicated operational phones
- Developers or operators testing local SMS automation
- Internal or sideloaded deployments

The MVP is not intended for bulk outreach, advertising, lead generation, or unsolicited messaging.

## 4. User Stories

A user can:

- create a rule with a name
- optionally restrict a rule to one incoming sender
- enter a regex for the incoming body
- configure one outgoing destination
- build an outgoing message using template variables
- test the sender, regex, capture groups, and rendered message without sending
- enable or disable a rule
- disable all automation with one master switch
- review whether a rule matched and whether sending succeeded
- understand why a send failed
- delete a rule

## 5. MVP Screens

### Onboarding

Explain why receive and send permissions are needed before requesting them.

Show:

- receive permission state
- send permission state
- action to open system settings after permanent denial

### Rules List

Each item should show:

```text
rule name
enabled state
sender filter or Any sender
regex preview
masked destination
last execution result
```

Actions:

- create
- edit
- enable or disable
- delete

### Rule Editor

Fields:

```text
Rule name
Incoming sender
Message regex
Destination number
Outgoing template
Enabled
```

Validation:

- name is required
- regex compiles
- destination is not empty and appears valid
- template is not empty
- referenced capture groups produce warnings when unavailable in the test case

### Rule Tester

Inputs:

```text
sample sender
sample message
```

Outputs:

```text
sender match
regex match
capture groups
rendered outgoing message
warnings
```

The tester must not invoke the SMS sender.

### History

Show:

```text
time
rule name
match status
send status
masked destination
failure reason when applicable
```

Avoid storing full incoming content by default.

### Settings

MVP settings:

- master automation switch
- permission status
- history retention
- privacy information
- app version

## 6. Rule Behavior

Evaluation order:

```text
deduplicate incoming event
load enabled rules
sender comparison
regex evaluation
template rendering
safety and rate-limit checks
send
log result
```

Rules execute in deterministic order. For the MVP, all matching enabled rules may execute.

An empty sender filter means any sender. A non-empty sender filter uses exact normalized comparison.

Regex uses contains/find behavior. Full-message matching requires anchors supplied by the user.

## 7. Template Variables

MVP variables:

```text
{{message}}
{{sender}}
{{match_0}}
{{match_1}}
{{match_2}}
{{timestamp}}
```

`match_0` is the complete regex match. Higher indexes are capture groups.

The template language must not support code execution, file access, network access, or arbitrary expressions.

## 8. Permission Behavior

When receive permission is missing:

- rule management and testing remain available
- automatic incoming-SMS processing is unavailable

When send permission is missing:

- rules may still be tested
- no automatic SMS is sent
- the failure is visible in history or status UI

Do not launch permission UI unexpectedly from a background receiver.

## 9. Error States

At minimum, represent:

```text
RECEIVE_PERMISSION_MISSING
SEND_PERMISSION_MISSING
INVALID_REGEX
INVALID_DESTINATION
DUPLICATE_EVENT
RATE_LIMITED
NO_ACTIVE_SUBSCRIPTION
NO_SERVICE
RADIO_OFF
TEMPLATE_ERROR
SEND_FAILED
```

User-facing messages should be understandable and avoid raw exception text.

## 10. Privacy Expectations

Default behavior:

```text
SMS processing: local
Rules: local
History: local
Cloud upload: none
Analytics: none for MVP
Internet permission: not required
```

History should store masked or truncated previews unless the user explicitly opts into more detail in a later version.

## 11. MVP Acceptance Criteria

### Basic match

Given an enabled rule with a matching sender and regex:

- the rule matches once
- capture groups are available
- one outgoing send is attempted
- the result is recorded

### No match

An unrelated SMS causes no outgoing send.

### Disabled rule

A disabled rule never sends.

### Duplicate event

The same logical incoming SMS processed twice causes at most one outgoing send per rule.

### Invalid regex

An invalid enabled rule cannot be saved.

### Missing send permission

The app does not send and does not crash. The failure is visible.

### Multipart incoming SMS

Segments are reconstructed and evaluated once as one body.

### Long outgoing message

The outgoing body is divided into SMS parts and sent as one logical action.

### Master switch

When automation is off, no incoming SMS triggers an outgoing SMS.

### Rate limiting

A burst beyond configured limits is blocked and recorded.

## 12. Non-Goals

The MVP will not:

- replace the user's full SMS application
- import or scan historical messages
- provide a cloud dashboard
- support multiple communication channels
- perform bulk or scheduled campaigns
- classify content with machine learning
- automatically discover contacts

## 13. Release Positioning

The first release should be treated as a development, internal, or sideloaded build. Public store distribution requires a separate review of current SMS permission and default-handler policies.

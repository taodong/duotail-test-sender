# Bug Report: `getMailhogMessage` Fails Due to ID Mismatch Between Email MCP Tools

**Date:** 2026-05-14  
**Model:** DeepSeek v4 Pro  
**Test:** v0.3.0\_1\_test\_register\_account  
**Status:** Closed — Fixed in commit on 2026-05-14  
**Severity:** Medium — adds ~18 extra steps and ~$0.026 per affected run

---

## Summary

The agent called `getMailhogMessage` with an ID value derived from the email's
`Message-ID` header, but the tool expects the MailHog internal document ID. Because
`searchMailhogMessages` does not expose the internal ID in its output, the agent had no
valid ID to pass. The tool returned a null-dereference error, forcing the agent to
fall back to navigating the MailHog UI manually to retrieve the activation link.

---

## Reproduction

**Task:** Test Register and Activate Account  
**Step:** Email Check — retrieve the activation link from the confirmation email

**Trigger sequence:**

1. Agent calls `searchMailhogMessages` to locate the confirmation email
2. Tool returns metadata only (subject, from, to, created) — no internal ID exposed
3. Agent infers an ID from the `From` domain pattern and passes it to `getMailhogMessage`
4. Tool fails with a null-dereference error
5. Agent falls back to `listMailhogMessages` — same metadata, still no internal ID
6. Agent falls back to MailHog browser UI — adds 18 extra steps to retrieve the link

---

## Log Trace

### Step 21 — `searchMailhogMessages` (success, but incomplete output)

```
INPUT:
{
  "kind": "to",
  "query": "dwtest.user7@enduser1.com",
  "start": 0,
  "limit": 5
}

RESULT:
Found 1 message(s). Showing 1 from offset 0.

1. Subject: New Email Confirmation
   From: donotreply@duotail.com
   To: dwtest.user7@enduser1.com
   Created: 2026-05-14T21:18:18.619593138Z
```

**Problem:** No internal ID field returned. The agent has no valid ID to pass to
`getMailhogMessage`.

---

### Step 23 — `getMailhogMessage` (failure)

```
INPUT:
{
  "id": "G3vPRaDolQCGsQdwLIqKtGa1duCauzSy@donotreply.duotail.com"
}

RESULT:
Error invoking method: getMailhogMessage
Cannot invoke "com.duotail.utils.email.mailhog.dto.MailhogMessage.content()"
because "msg" is null
```

**Root cause:** The ID `G3vPRaDolQCGsQdwLIqKtGa1duCauzSy@donotreply.duotail.com`
is an SMTP `Message-ID` header value constructed by the agent from the From domain
pattern. The MailHog API uses an internal document ID (e.g. a UUID or hash) that is
not exposed by either `searchMailhogMessages` or `listMailhogMessages`. When no
matching document is found, the server returns `null` and the tool crashes on
`.content()`.

---

### Step 24–25 — `listMailhogMessages` (success, but still no internal ID)

```
INPUT:
{ "start": 0, "limit": 5 }

RESULT:
Found 6 message(s). Showing 5 from offset 0.

1. Subject: New Email Confirmation | From: donotreply@duotail.com
   To: dwtest.user7@enduser1.com | Created: 2026-05-14T21:18:18.619593138Z
2. Subject: News A Daily Brief | From: 1_a_duotest506_news@duotail.com ...
3. Subject: New Email Confirmation | From: donotreply@duotail.com ...
   To: duotest506@enduser1.com ...
...
```

**Same gap:** `listMailhogMessages` returns identical metadata fields without the
internal ID. Agent concludes it cannot retrieve the message body via API.

---

### Steps 27–44 — MailHog UI fallback (18 steps, 2 snapshot errors)

Having exhausted the Email MCP tools, the agent navigated the MailHog web UI
manually to open the email and read the activation link.

```
[27] browser_navigate  url=http://host.docker.internal:38025
[28] browser_click     target=e23  (Inbox listitem)
[29] take_verification_snapshot  --> snapshot s_c2yz7t
[30] browser_click     target=e98  (email row)
[31] take_verification_snapshot  --> ERROR: Too many active verification snapshots
[32] complete_verification       snapshot_id=s_c2yz7t  (forced release)
[33] context-manager   action=summary
[34] searchMailhogMessages       (retry, same result — no ID)
[35] listMailhogMessages         (retry, same result — no ID)
[36] browser_navigate  url=http://host.docker.internal:38025
[37] take_verification_snapshot  --> snapshot s_ja4ovn
[38] (text) Clicking the confirmation email...
[39] browser_click     target=e44  (email row)
[40] take_verification_snapshot  --> ERROR: Too many active verification snapshots
[41] complete_verification       snapshot_id=s_ja4ovn  (forced release)
[42] take_verification_snapshot  --> snapshot s_u11mlo  (email content with link)
[43] (text) Found the activation link.
[44] complete_verification       step=Email activation link  PASS
     detail: Found confirmation link with token a75e7828-9dc7-4661-b8a2-92b0b374e2a9
```

**Side effect:** The MailHog UI fallback also triggered 2 `Too many active verification
snapshots` errors (steps 31 and 40) because the agent clicked email rows without
releasing the active snapshot first.

---

## Impact

| Metric | With bug | Without bug (ideal) |
|--------|---------|-------------------|
| Steps to retrieve activation link | 24 (steps 21–44) | 2 (search + get) |
| Extra API calls | ~8 | — |
| Extra cost per run | ~$0.026 | — |
| Snapshot errors triggered | 2 | 0 |

---

## Root Cause

The `searchMailhogMessages` and `listMailhogMessages` tools do not expose the MailHog
internal document ID in their output. The `getMailhogMessage` tool requires this
internal ID. There is no documented path from the search/list tools to a valid ID for
`getMailhogMessage`, creating a broken tool chain.

The agent attempted to bridge the gap by constructing an ID from available fields, but
the value it produced (`G3vPRaDolQCGsQdwLIqKtGa1duCauzSy@donotreply.duotail.com`) is
the SMTP `Message-ID` header — a different identifier — not the MailHog internal ID.

---

## Recommended Fixes

### Fix 1 — Expose internal ID in `searchMailhogMessages` and `listMailhogMessages` output (recommended)

Update both tools to include the MailHog internal document ID in every result entry:

```
1. Subject: New Email Confirmation
   From: donotreply@duotail.com
   To: dwtest.user7@enduser1.com
   Created: 2026-05-14T21:18:18.619593138Z
   ID: <mailhog-internal-id>        ← add this field
```

This makes the tool chain `searchMailhogMessages` → `getMailhogMessage` complete
without any agent inference or workaround.

### Fix 2 — Embed message body in `searchMailhogMessages` result (alternative)

If exposing the internal ID is not feasible, return the message body (or at minimum
the plain-text part) directly in the `searchMailhogMessages` result. This eliminates
the need for `getMailhogMessage` entirely for the activation link use case:

```
1. Subject: New Email Confirmation
   From: donotreply@duotail.com
   To: dwtest.user7@enduser1.com
   Body: Please confirm your email by clicking the following link:
         http://localhost:30080/confirm-email?token=...
```

### Fix 3 — Add null guard in `getMailhogMessage` server implementation (defensive)

The server crashes with a `NullPointerException` when no message matches the given ID:

```
Cannot invoke "MailhogMessage.content()" because "msg" is null
```

Regardless of the ID format issue, this should return a structured error response
rather than throwing a `NullPointerException`:

```json
{
  "error": "Message not found",
  "id": "<the-id-that-was-passed>"
}
```

This would at minimum make the failure mode clearer and easier to diagnose without
reading server logs.

---

## Resolution

**Fix 1 — ID exposed in list/search output**

`McpToolService.formatResponse()` now appends `| ID: <id>` to every result entry. Both
`listMailhogMessages` and `searchMailhogMessages` include the MailHog internal document ID,
making the `searchMailhogMessages → getMailhogMessage` chain complete without any agent
inference.

**Fix 3 — Null-body guard in `MailhogService.getMessage()`**

After `body(MailhogMessage.class)`, a null check now throws `MailhogMessageNotFoundException`
immediately. The existing 404 handler only covered HTTP-level not-found responses; this closes
the gap where MailHog returns a 200 with a null/empty body (e.g. due to a malformed ID in the
path segment), which previously propagated null to the caller and caused the NPE in
`McpToolService`.

Fix 2 (embed body in search results) was deferred — it requires DTO changes and solves a
different problem.

---

## Observed Across Runs

| Run | Tool used | Outcome |
|-----|-----------|---------|
| 2026-05-13 06:xx (DeepSeek v1) | `browser_navigate` to MailHog UI directly | No `getMailhogMessage` attempt — avoided the bug |
| 2026-05-14 00:xx (DeepSeek v2) | `browser_run_code_unsafe` fetch attempt + UI fallback | `fetch()` blocked by CORS, then UI fallback |
| 2026-05-14 21:xx (DeepSeek v3) | `getMailhogMessage` with wrong ID + UI fallback | Tool error, then UI fallback — this report |

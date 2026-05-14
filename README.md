# Duotail Test Sender — User Manual

## Overview

Duotail Test Sender is a utility service for sending test emails. It exposes seven RESTful endpoints and a matching set of MCP tools, all backed by the same `EmailSendService`.

Assumptions:
1. All sending emails are test email to a SMTP server without authentication (e.g. MailHog, Postfix).
2. All emails are stored in a MailHog server, which provides a REST API for retrieval and search.

---

## Authentication / Version Header

Every REST endpoint requires the HTTP header:

```
version: 1
```

Requests missing this header will be rejected with `404 Not Found`.

---

## RESTful API

### 1. Send Single Email

Send one HTML email using a structured JSON body.

| Property | Value |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/email` |
| **Content-Type** | `application/json` |
| **Required header** | `version: 1` |

#### Request Body (`EmailRequest`)

| Field | Type | Required | Description |
|---|---|---|---|
| `from` | `string` | Yes | Sender email address (must be a valid email format) |
| `to` | `string[]` | Yes | One or more primary recipient email addresses |
| `cc` | `string[]` | No | CC (carbon copy) recipient email addresses |
| `bcc` | `string[]` | No | BCC (blind carbon copy) recipient email addresses |
| `subject` | `string` | Yes | Subject line of the email |
| `content` | `string` | Yes | Body content of the email (HTML is supported) |
| `extraHeaders` | `object` | No | Custom SMTP headers as key-value pairs |
| `messageId` | `string` | No | Unique ID to track this message; auto-generated if omitted |

#### Response

| Status | Body | Condition |
|---|---|---|
| `200 OK` | _(empty)_ | Email sent successfully |
| `400 Bad Request` | Validation error details | Request body fails validation |
| `403 Forbidden` | Error message | Sender or recipient not permitted |
| `500 Internal Server Error` | Error message | SMTP or other server error |

#### Example

```bash
curl -X POST http://localhost:8080/api/email \
  -H "version: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "from": "sender@example.com",
    "to": ["recipient@example.com"],
    "cc": ["cc@example.com"],
    "subject": "Hello from Test Sender",
    "content": "<h1>Hello!</h1><p>This is a test email.</p>"
  }'
```

---

### 2. Send Batch Emails

Send multiple emails in a single request. Processing is best-effort — failures for individual emails are logged but do not abort the batch.

| Property | Value |
|---|---|
| **Method** | `POST` |
| **Paths** | `/api/emails` or `/api/mails` |
| **Content-Type** | `application/json` |
| **Required header** | `version: 1` |

#### Request Body (`BatchEmailRequest`)

| Field | Type | Required | Description |
|---|---|---|---|
| `emails` | `EmailRequest[]` | Yes | Non-empty list of email request objects (see [EmailRequest fields](#request-body-emailrequest) above) |

#### Response

| Status | Body | Condition |
|---|---|---|
| `200 OK` | _(empty)_ | Batch processing completed |
| `400 Bad Request` | Validation error details | Request body fails validation |
| `403 Forbidden` | Error message | Sender or recipient not permitted |
| `500 Internal Server Error` | Error message | SMTP or other server error |

#### Example

```bash
curl -X POST http://localhost:8080/api/emails \
  -H "version: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "emails": [
      {
        "from": "sender@example.com",
        "to": ["alice@example.com"],
        "subject": "Email 1",
        "content": "<p>First email</p>"
      },
      {
        "from": "sender@example.com",
        "to": ["bob@example.com"],
        "subject": "Email 2",
        "content": "<p>Second email</p>"
      }
    ]
  }'
```

---

### 3. Send Raw EML File

Upload a raw `.eml` file as a multipart form. Optionally override the `from`, `to`, `cc`, and `bcc` headers in the file.

| Property | Value |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/eml` |
| **Content-Type** | `multipart/form-data` |
| **Required header** | `version: 1` |

#### Form Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file` | file | Yes | The `.eml` file to send |
| `from` | `string` | No | Override the sender address in the email header |
| `to` | `string[]` | No | Override the `To` recipients in the email header |
| `cc` | `string[]` | No | Override the `CC` recipients in the email header |
| `bcc` | `string[]` | No | Override the `BCC` recipients in the email header |

#### Response

| Status | Body | Condition |
|---|---|---|
| `200 OK` | `"Email Sent."` | EML file processed and sent |
| `403 Forbidden` | Error message | Sender or recipient not permitted |
| `500 Internal Server Error` | `"Failed to send email: <reason>"` | File parse error or SMTP failure |

#### Example

```bash
curl -X POST http://localhost:8080/api/eml \
  -H "version: 1" \
  -F "file=@/path/to/message.eml" \
  -F "from=override-sender@example.com" \
  -F "to=override-recipient@example.com"
```

---

### 4. Retrieve Captured Emails (MailHog)

Retrieve emails captured by a MailHog server, newest first.

| Property | Value |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/email/messages` |
| **Required header** | `version: 1` |

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `start` | integer | No | `0` | Zero-based offset into the message list |
| `limit` | integer | No | `50` | Maximum number of messages to return |

#### Response

| Status | Body | Condition |
|---|---|---|
| `200 OK` | `MailhogPageResponse` JSON (see below) | Messages retrieved successfully |
| `404 Not Found` | `{ "message": "..." }` | MailHog server is unreachable or returned an error |

**Response body shape:**

```json
{
  "total": 42,
  "count": 10,
  "start": 0,
  "items": [
    {
      "id": "abc123",
      "from": { "mailbox": "sender", "domain": "example.com" },
      "to": [{ "mailbox": "recipient", "domain": "example.com" }],
      "content": {
        "headers": { "Subject": ["Hello"] },
        "size": 512
      },
      "created": "2025-01-01T10:00:00Z"
    }
  ]
}
```

#### Example

```bash
curl http://localhost:8080/api/email/messages \
  -H "version: 1"

# With pagination
curl "http://localhost:8080/api/email/messages?start=10&limit=5" \
  -H "version: 1"
```

---

### 5. Search Captured Emails (MailHog)

Search emails captured by a MailHog server by sender, recipient, or body content.

| Property | Value |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/email/search` |
| **Required header** | `version: 1` |

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `kind` | string | Yes | — | Search mode: `from`, `to`, or `containing` |
| `query` | string | Yes | — | Search term |
| `start` | integer | No | `0` | Zero-based offset into the result list |
| `limit` | integer | No | `50` | Maximum number of results to return |

#### Response

| Status | Body | Condition |
|---|---|---|
| `200 OK` | `MailhogPageResponse` JSON (same shape as § 4) | Search completed successfully |
| `400 Bad Request` | Validation error details | `kind` or `query` is missing |
| `404 Not Found` | `{ "message": "..." }` | MailHog server is unreachable or returned an error |

#### Example

```bash
# Find emails sent from a specific address
curl "http://localhost:8080/api/email/search?kind=from&query=sender@example.com" \
  -H "version: 1"

# Find emails whose body contains a keyword
curl "http://localhost:8080/api/email/search?kind=containing&query=activation+code" \
  -H "version: 1"
```

---

### 6. Get Single Captured Email (MailHog)

Retrieve a single email captured by a MailHog server by its message ID.

| Property | Value |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/email/messages/{id}` |
| **Required header** | `version: 1` |

#### Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | string | Yes | The MailHog message ID |

#### Response

| Status | Body | Condition |
|---|---|---|
| `200 OK` | `MailhogMessage` JSON (same shape as items in § 4) | Message retrieved successfully |
| `404 Not Found` | `{ "message": "MailHog message not found: {id}" }` | No message exists with the given ID |
| `404 Not Found` | `{ "message": "..." }` | MailHog server is unreachable or returned an error |

#### Example

```bash
curl http://localhost:8080/api/email/messages/abc123 \
  -H "version: 1"
```

---

### 7. Delete Captured Email (MailHog)

Delete a single email captured by a MailHog server by its message ID.

| Property | Value |
|---|---|
| **Method** | `DELETE` |
| **Path** | `/api/email/messages/{id}` |
| **Required header** | `version: 1` |

#### Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | string | Yes | The MailHog message ID |

#### Response

| Status | Body | Condition |
|---|---|---|
| `204 No Content` | _(empty)_ | Message deleted successfully |
| `404 Not Found` | `{ "message": "MailHog message not found: {id}" }` | No message exists with the given ID |
| `404 Not Found` | `{ "message": "..." }` | MailHog server is unreachable or returned an error |

#### Example

```bash
curl -X DELETE http://localhost:8080/api/email/messages/abc123 \
  -H "version: 1"
```

---

## MCP Tools

The service registers the following tools with the MCP (Model Context Protocol) server. They mirror the REST API and share the same underlying `EmailSendService`.

### Tool: `sendEmail`

**Description:** Send one HTML email using a structured request object.

**Parameters:** An `EmailRequest` object with the following fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `from` | `string` | Yes | Sender email address (must be a valid email format) |
| `to` | `string[]` | Yes | One or more primary recipient email addresses |
| `cc` | `string[]` | No | CC recipient email addresses |
| `bcc` | `string[]` | No | BCC recipient email addresses |
| `subject` | `string` | Yes | Subject line of the email |
| `content` | `string` | Yes | Body content of the email (HTML supported) |
| `extraHeaders` | `object` | No | Custom SMTP headers as key-value pairs |
| `messageId` | `string` | No | Unique tracking ID; auto-generated if omitted |

**Returns:** `"Email sent successfully."` on success.

---

### Tool: `sendBatchEmails`

**Description:** Best-effort send for multiple emails in a single batch. Individual failures are logged but do not stop processing.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `emails` | `EmailRequest[]` | Yes | List of email request objects (same fields as `sendEmail` above) |

**Returns:** `"Batch send triggered for N emails."` where `N` is the number of emails in the list.

---

### Tool: `sendEmlFileBase64`

**Description:** Send a raw `.eml` payload represented as a base64-encoded string. Optionally override the sender and recipients from the email headers.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `emlBase64` | `string` | Yes | Raw `.eml` file content encoded in base64 |
| `from` | `string` | No | Override the `from` address in the email header |
| `to` | `string[]` | No | Override the `to` addresses in the email header |
| `cc` | `string[]` | No | Override the `cc` addresses in the email header |
| `bcc` | `string[]` | No | Override the `bcc` addresses in the email header |

**Returns:** `"EML email sent successfully."` on success.

---

### Tool: `listMailhogMessages`

**Description:** List emails captured by MailHog. Returns a plain-text summary of each message.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `start` | integer | Yes | Zero-based start offset |
| `limit` | integer | Yes | Max number of messages to return (max 50) |

**Returns:** A plain-text summary, one line per message:

```
Found {total} message(s). Showing {count} from offset {start}.

1. Subject: Hello World | From: sender@example.com | To: recipient@example.com | Created: 2025-01-01T10:00:00Z
2. Subject: (no subject) | From: ...
```

Subject falls back to `(no subject)` if the header is absent.

---

### Tool: `searchMailhogMessages`

**Description:** Search emails in MailHog by sender address, recipient address, or body content.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `kind` | string | Yes | Search mode: `from`, `to`, or `containing` |
| `query` | string | Yes | Search term |
| `start` | integer | Yes | Zero-based start offset |
| `limit` | integer | Yes | Max number of results to return (max 50) |

**Returns:** Same plain-text summary format as `listMailhogMessages`.

---

### Tool: `getMailhogMessage`

**Description:** Get a specific email captured by MailHog by its message ID.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | string | Yes | The MailHog message ID |

**Returns:** A plain-text block:

```
Message ID: {id}
Subject: {subject}
From: {from}
To: {to}
Created: {created}
```

Subject falls back to `(no subject)` if the header is absent.

---

### Tool: `deleteMailhogMessage`

**Description:** Delete a specific email captured by MailHog by its message ID.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | string | Yes | The MailHog message ID |

**Returns:** `"Message {id} deleted successfully."` on success.

---

## Configuration

### `application.yaml` Property

| Key | Env Var / System Property | Description | Type | Default | Required |
|-----|--------------------------|-------------|------|---------|----------|
| `spring.mail.host` | `mail-host` | SMTP server hostname | String | `localhost` | No |
| `spring.mail.port` | `mail-port` | SMTP server port | Integer | `25` | No |
| `spring.mail.properties.mail.smtp.auth` | `mail-smtp-auth` | Enable SMTP authentication | Boolean | `false` | No |
| `spring.mail.properties.mail.smtp.starttls.enable` | `mail-smtp-starttls-enable` | Enable STARTTLS | Boolean | `false` | No |
| `app.permissions` | `permissions-file` | Path to the permissions file. Supports Spring resource prefixes (`classpath:`, `file:`). | String | `classpath:permissions.yaml` | No |
| `app.mailhog.url` | `mailhog-url` | URL of the MailHog server to query for email retrieval and search | String | `http://localhost:8025` | No |
| `logging.file.path` | `LOG_DIR` | Directory where log files are written | String | `.` (current directory) | No |

### `permissions.yaml` File Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `from` | Contact permission block | No | Controls which sender (`From`) addresses are allowed |
| `to` | Contact permission block | No | Controls which recipient (`To`, `CC`, `BCC`) addresses are allowed |
| `batchSize` | Integer | No | Maximum number of emails allowed in a single batch request. Defaults to `10000` if absent or invalid |

**Contact Permission Block** (applies to both `from` and `to`):

| Field | Type | Description |
|-------|------|-------------|
| `domains` | List of strings | Allowed sender/recipient domains. Supports wildcard `"*"` to allow all domains |
| `emails` | List of strings | Allowed full email addresses. Only evaluated when `domains` is not set |

> `domains` takes precedence over `emails`. If both are specified, `emails` is ignored.

**Permission Check Logic** (evaluated in order):
1. If `domains` contains `"*"` — all addresses pass.
2. If specific domains are listed — the email's domain must exactly match or be a subdomain.
3. If no domains are configured — the full email address must match an entry in `emails` (case-insensitive).
4. If none of the above are configured — request is rejected with `403 Forbidden`.

**Default `permissions.yaml`:**

```yaml
from:
  emails:
    - "from@example.com"
to:
  emails:
    - "to@example.com"
batchSize: 10
```

**Common Configuration Examples:**

```yaml
# Open relay (allow all senders and recipients)
from:
  domains:
    - "*"
to:
  domains:
    - "*"
batchSize: 10000
```

```yaml
# Restrict to a single domain (subdomains included automatically)
from:
  domains:
    - "mycompany.com"
to:
  domains:
    - "mycompany.com"
batchSize: 100
```

```yaml
# Restrict to specific email addresses
from:
  emails:
    - "noreply@mycompany.com"
    - "alerts@mycompany.com"
to:
  emails:
    - "team@mycompany.com"
batchSize: 25
```

---

## Environment Variables

| Variable | Description | Required | Default | Example |
|----------|-------------|----------|---------|---------|
| `mail-host` | SMTP server hostname | No | `localhost` | `smtp.mailhog.local` |
| `mail-port` | SMTP server port | No | `25` | `587` |
| `mail-smtp-auth` | Enable SMTP authentication | No | `false` | `true` |
| `mail-smtp-starttls-enable` | Enable STARTTLS on the SMTP connection | No | `false` | `true` |
| `permissions-file` | Overrides the path to the permissions YAML file. Accepts Spring resource prefixes (`classpath:`, `file:`). Can also be passed as a Java system property (`-Dpermissions-file=...`). | No | `classpath:permissions.yaml` | `file:/etc/email-sender/permissions.yaml` |
| `LOG_DIR` | Directory where log files are written | No | `.` (current directory) | `/var/log/email-sender` |
| `mailhog-url` | URL of the MailHog server used for email retrieval and search | No | `http://localhost:8025` | `http://mailhog:8025` |

---

## Error Reference

| Error | HTTP Status | MCP Behavior | Cause |
|---|---|---|---|
| `PermissionException` | `403 Forbidden` | Tool throws exception | Sender address or recipient not in the allowed permissions list |
| `MessagingException` | `500 Internal Server Error` | Tool throws exception | SMTP failure or malformed email |
| Validation failure | `400 Bad Request` | Tool throws exception | Required fields missing or invalid format |
| EML parse/IO error | `500 Internal Server Error` | Tool throws exception | Corrupted or unreadable `.eml` content |
| `MailhogUnavailableException` | `404 Not Found` | Tool throws exception | MailHog server is unreachable or returned an error |
| `MailhogMessageNotFoundException` | `404 Not Found` | Tool throws exception | Message ID not found in MailHog |

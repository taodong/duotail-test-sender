# TestEmailSender
A test email sender

## MCP Server
The app now exposes a minimal MCP-compatible JSON-RPC endpoint at `POST /mcp`.

Supported MCP methods:
- `initialize`
- `tools/list`
- `tools/call`

Registered tools:
- `send_email`
- `send_batch_emails`
- `send_eml_file_base64`

Example:

```shell
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Initialize session:

```shell
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

Call `send_email`:

```shell
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc":"2.0",
    "id":2,
    "method":"tools/call",
    "params":{
      "name":"send_email",
      "arguments":{
        "from":"sender@example.com",
        "to":["receiver@example.com"],
        "subject":"Hello",
        "content":"<p>Hello from MCP</p>"
      }
    }
  }'
```

Call `send_batch_emails`:

```shell
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc":"2.0",
    "id":3,
    "method":"tools/call",
    "params":{
      "name":"send_batch_emails",
      "arguments":{
        "emails":[
          {
            "from":"sender@example.com",
            "to":["one@example.com"],
            "subject":"Batch 1",
            "content":"<p>First</p>"
          },
          {
            "from":"sender@example.com",
            "to":["two@example.com"],
            "subject":"Batch 2",
            "content":"<p>Second</p>"
          }
        ]
      }
    }
  }'
```

Call `send_eml_file_base64`:

```shell
EML_BASE64="$(printf 'From: sender@example.com\nTo: receiver@example.com\nSubject: EML test\n\nHello from raw EML.' | base64)"

curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"send_eml_file_base64\",\"arguments\":{\"emlBase64\":\"${EML_BASE64}\"}}}"
```


## Java / Spring Boot Upgrade
Upgrade through OpenRewrite

```shell
mvn rewrite:run
```

- SpringBoot 4 upgrade: https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0-community-edition
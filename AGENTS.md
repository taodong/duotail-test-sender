# AGENTS.md

## Project Snapshot
- Spring Boot 4.0.3 + Java 25 REST utility for relaying test emails (`pom.xml`).
- Main package boundary is `com.duotail.utils.email`; all API and mail logic is in `src/main/java/com/duotail/utils/email/sender`.
- Service boundary is thin: controllers only validate/map requests, `EmailSendService` does all message construction and sending.

## Architecture and Data Flow
- `EmailSendController` (`POST /api/email`) -> `EmailSendService.sendEmail(EmailRequest)`.
- `BatchEmailSendController` (`POST /api/emails`) -> `EmailSendService.sendEmails(Collection<EmailRequest>)`.
- `EmlFileUploadController` (`POST /api/eml`, multipart `file`) -> `EmailSendService.sendEmailInFile(InputStream)`.
- Every endpoint requires request header `version=1` because of class-level `@RequestMapping(..., headers = "version=1")`.
- `EmailRequest` is the core contract: required `from`, `to`, `subject`, `content`; optional `cc`, `bcc`, `extraHeaders`, `messageId`.
- `sendEmail` creates a `jakarta.mail.internet.MimeMessage` manually and sends via injected `JavaMailSender`.

## Code Patterns to Preserve
- Keep controller classes minimal; put email assembly/sending behavior in `EmailSendService`.
- Preserve Jakarta validation annotations on DTOs (`@NotBlank`, `@NotEmpty`) and `@Valid` on controller request bodies.
- HTML mode is intentional: `MimeMessageHelper.setText(content, true)` in `EmailSendService`.
- Batch behavior is best-effort by design: `sendEmails` logs per-email failures and continues.
- Lombok logging field is `LOG` (configured in `lombok.config`), not the default `log`.

## Integration and Config Notes
- SMTP config is environment-driven in `src/main/resources/application.yaml`:
  - `mail-host` (default `localhost`), `mail-port` (default `25`), auth/starttls toggles.
- External integration surface is only SMTP through `spring-boot-starter-mail`.
- `sendEmailInFile` currently parses raw `.eml` from input stream and sends it as-is.

## Developer Workflows
- Run tests: `./mvnw test`
- Run app locally: `./mvnw spring-boot:run`
- Build jar: `./mvnw clean package`
- Apply framework modernization recipe (already configured): `./mvnw rewrite:run`
- Fast endpoint sanity check must include header, e.g. `curl -H 'version: 1' ...`.

## Test and Change Guidance
- Existing unit coverage is focused on `EmailSendService.sendEmail` interaction behavior (`src/test/java/com/duotail/utils/email/sender/EmailSendServiceTest.java`).
- For new endpoint logic, add controller tests for `version` header gating and validation failures.
- For service changes, prefer interaction tests verifying `MimeMessageHelper` calls and `JavaMailSender.send(...)` invocation.


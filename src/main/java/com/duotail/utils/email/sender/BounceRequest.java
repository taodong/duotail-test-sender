package com.duotail.utils.email.sender;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request describing a mocked bounce (RFC 3464 Delivery Status Notification) to generate and send
 * back to the original sender of a message.
 */
@Data
public class BounceRequest {

    @NotBlank
    @Email
    @JsonPropertyDescription("The original sender's email address; the bounce (NDR) is delivered back to this address")
    private String originalFrom;

    @NotBlank
    @Email
    @JsonPropertyDescription("The recipient whose delivery failed; reported as the Final-Recipient in the DSN")
    private String originalTo;

    @NotBlank
    @JsonPropertyDescription("The subject line of the original message; echoed in the wrapped headers and bounce body")
    private String originalSubject;

    @JsonPropertyDescription("Optional: Message-ID of the original message; referenced by the DSN. Generated if omitted")
    private String originalMessageId;

    @NotNull
    @JsonPropertyDescription("Bounce category: HARD (permanent, 5.x.x) or SOFT (transient, 4.x.x)")
    private BounceType bounceType;

    @JsonPropertyDescription("Optional: RFC 3463 status code (e.g. 5.1.1, 4.2.2). Must match the bounceType class. Defaults per bounceType")
    private String statusCode;

    @JsonPropertyDescription("Optional: human-readable failure reason for the Diagnostic-Code. Defaults from a built-in map")
    private String diagnosticText;

    @JsonPropertyDescription("Optional: the MTA name emitted in Reporting-MTA. Defaults to the configured value")
    private String reportingMta;

    @JsonIgnore
    @AssertTrue(message = "statusCode class must match bounceType (5.x.x for HARD, 4.x.x for SOFT)")
    public boolean isStatusCodeConsistent() {
        if (statusCode == null || bounceType == null) {
            return true;
        }
        return statusCode.startsWith(bounceType.getStatusClassPrefix());
    }
}

package com.duotail.utils.email.sender;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class EmailRequest {
    @NotBlank
    @Email
    @JsonPropertyDescription("The sender's email address (must be a valid email format)")
    private String from;
    @NotEmpty
    @JsonPropertyDescription("A set of primary recipient email addresses")
    private Set<String> to;
    @JsonPropertyDescription("Optional: A set of CC (carbon copy) recipient email addresses")
    private Set<String> cc;
    @JsonPropertyDescription("Optional: A set of BCC (blind carbon copy) recipient email addresses")
    private Set<String> bcc;
    @NotBlank
    @NotBlank
    @JsonPropertyDescription("The subject line of the email")
    private String subject;
    @NotBlank
    @JsonPropertyDescription("The main body content of the email (HTML is supported)")
    private String content;
    @JsonPropertyDescription("Optional: Custom SMTP headers as key-value pairs")
    private Map<String, String> extraHeaders = new HashMap<>();
    @JsonPropertyDescription("Optional: A unique ID to track this message; if omitted, one will be generated")
    private String messageId;
}

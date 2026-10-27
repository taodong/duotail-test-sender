package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.validation.MailboxAddress;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class EmailRequest {
    @NotBlank
    @MailboxAddress
    @JsonPropertyDescription("The sender's email address, either 'address' or 'Name <address>'")
    private String from;
    @NotEmpty
    @JsonPropertyDescription("A set of primary recipient email addresses, each either 'address' or 'Name <address>'")
    private Set<@MailboxAddress String> to;
    @JsonPropertyDescription("Optional: A set of CC (carbon copy) recipient email addresses, each either 'address' or 'Name <address>'")
    private Set<@MailboxAddress String> cc;
    @JsonPropertyDescription("Optional: A set of BCC (blind carbon copy) recipient email addresses, each either 'address' or 'Name <address>'")
    private Set<@MailboxAddress String> bcc;
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

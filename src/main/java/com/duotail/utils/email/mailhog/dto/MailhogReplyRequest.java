package com.duotail.utils.email.mailhog.dto;

import com.duotail.utils.email.sender.validation.MailboxAddress;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to reply (or reply all) to a message captured by MailHog. Whether it is a reply or a
 * reply-all is decided by the caller, not by this request.
 */
@Data
public class MailhogReplyRequest {
    @NotBlank
    @MailboxAddress
    @JsonPropertyDescription("The replier's email address, either 'address' or 'Name <address>'")
    private String from;
    @NotBlank
    @JsonPropertyDescription("The reply body (HTML is supported); the original message is quoted below it")
    private String content;
}

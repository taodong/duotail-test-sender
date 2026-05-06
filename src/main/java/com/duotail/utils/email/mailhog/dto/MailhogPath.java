package com.duotail.utils.email.mailhog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailhogPath(
        @JsonProperty("Mailbox") String mailbox,
        @JsonProperty("Domain") String domain
) {
    public String address() {
        return mailbox + "@" + domain;
    }
}

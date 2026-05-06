package com.duotail.utils.email.mailhog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailhogMessage(
        @JsonProperty("ID") String id,
        @JsonProperty("From") MailhogPath from,
        @JsonProperty("To") List<MailhogPath> to,
        @JsonProperty("Content") MailhogContent content,
        @JsonProperty("Created") String created
) {}

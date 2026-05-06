package com.duotail.utils.email.mailhog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailhogContent(
        @JsonProperty("Headers") Map<String, List<String>> headers,
        @JsonProperty("Size") int size
) {}

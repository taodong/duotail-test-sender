package com.duotail.utils.email.mailhog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailhogPageResponse(
        int total,
        int count,
        int start,
        List<MailhogMessage> items
) {}

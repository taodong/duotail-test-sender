package com.duotail.utils.email.sender;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class EmailRequest {
    @NotBlank
    private String from;
    @NotEmpty
    private Set<String> to;
    private Set<String> cc;
    private Set<String> bcc;
    @NotBlank
    private String subject;
    @NotBlank
    private String content;
    private Map<String, String> extraHeaders = new HashMap<>();
}

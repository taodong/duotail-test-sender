package com.duotail.utils.email.sender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GmailRequest {
    @Email
    private String to;
    @NotBlank
    private String subject;
    @NotBlank
    private String content;
    private boolean html;
}

package com.duotail.utils.email.sender;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchEmailRequest {
    @NotEmpty
    private List<EmailRequest> emails;
}

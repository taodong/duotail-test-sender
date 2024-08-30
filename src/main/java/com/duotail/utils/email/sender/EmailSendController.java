package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping(value = "/api/email", headers = "version=1")
public class EmailSendController {
    private final EmailSendService emailSendService;

    public EmailSendController(EmailSendService emailSendService) {
        this.emailSendService = emailSendService;
    }

    @PostMapping
    public void sendEmail(@Valid @RequestBody EmailRequest emailRequest) throws MessagingException {
        emailSendService.sendEmail(emailRequest);
    }
}

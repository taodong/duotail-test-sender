package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/emails", headers = "version=1")
public class BatchEmailSendController {

    private final EmailSendService emailSendService;

    public BatchEmailSendController(EmailSendService emailSendService) {
        this.emailSendService = emailSendService;
    }

    @PostMapping
    public void sendEmail(@Valid @RequestBody BatchEmailRequest emailRequests) throws MessagingException {
        emailSendService.sendEmails(emailRequests.getEmails());
    }
}

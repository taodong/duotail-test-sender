package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = {"/api/emails", "/api/mails"}, headers = "version=1")
public class BatchEmailSendController {

    private final EmailSendService emailSendService;

    public BatchEmailSendController(EmailSendService emailSendService) {
        this.emailSendService = emailSendService;
    }

    @PostMapping
    public void sendEmail(@Valid @RequestBody BatchEmailRequest emailRequests) throws MessagingException, PermissionException {
        emailSendService.sendEmails(emailRequests.getEmails());
    }
}

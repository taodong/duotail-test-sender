package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/gmail", headers = "version=1")
public class GmailSendController {

    private final GmailSendService gmailSendService;

    public GmailSendController(GmailSendService gmailSendService) {
        this.gmailSendService = gmailSendService;
    }

    @PostMapping
    public void sendEmail(@Valid @RequestBody GmailRequest gmailRequest) throws MessagingException {
        gmailSendService.sendEmail(gmailRequest);
    }
}

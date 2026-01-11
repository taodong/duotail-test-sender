package com.duotail.utils.email.sender;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "/api/eml", headers = "version=1")
public class EmlFileUploadController {

    private final EmailSendService emailSendService;

    public EmlFileUploadController(EmailSendService emailSendService) {
        this.emailSendService = emailSendService;
    }

    @PostMapping
    public ResponseEntity<String> uploadEmlFile(@RequestParam("file") MultipartFile file) {

        try (var inputStream = file.getInputStream()) {
            emailSendService.sendEmailInFile(inputStream);
            return ResponseEntity.ok("Email Sent.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to send email: " + e.getMessage());
        }

    }
}

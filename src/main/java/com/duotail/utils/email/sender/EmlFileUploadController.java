package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping(value = "/api/eml", headers = "version=1")
public class EmlFileUploadController {

    private final EmailSendService emailSendService;

    public EmlFileUploadController(EmailSendService emailSendService) {
        this.emailSendService = emailSendService;
    }

    @PostMapping
    public ResponseEntity<String> uploadEmlFile(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "from", required = false) String from,
                                                 @RequestParam(value = "to", required = false) List<String> to,
                                                 @RequestParam(value = "cc", required = false) List<String> cc,
                                                 @RequestParam(value = "bcc", required = false) List<String> bcc)
            throws PermissionException {

        try (var inputStream = file.getInputStream()) {
            emailSendService.sendEmailInFile(inputStream, from, to, cc, bcc);
            return ResponseEntity.ok("Email Sent.");
        } catch (PermissionException exception) {
            throw exception;
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to send email: " + e.getMessage());
        }

    }
}

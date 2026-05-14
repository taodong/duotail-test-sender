package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.MailhogMessage;
import com.duotail.utils.email.mailhog.dto.MailhogPageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/email", headers = "version=1")
public class MailhogController {

    private final MailhogService mailhogService;

    public MailhogController(MailhogService mailhogService) {
        this.mailhogService = mailhogService;
    }

    @GetMapping("/messages")
    public ResponseEntity<MailhogPageResponse> getMessages(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(mailhogService.getMessages(start, limit));
    }

    @GetMapping("/messages/{id}")
    public ResponseEntity<MailhogMessage> getMessage(@PathVariable String id) {
        return ResponseEntity.ok(mailhogService.getMessage(id));
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        mailhogService.deleteMessage(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<MailhogPageResponse> search(
            @RequestParam String kind,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(mailhogService.search(kind, query, start, limit));
    }
}

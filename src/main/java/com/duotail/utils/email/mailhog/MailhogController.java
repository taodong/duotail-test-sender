package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.EmailContent;
import com.duotail.utils.email.mailhog.dto.MailhogMessage;
import com.duotail.utils.email.mailhog.dto.MailhogPageResponse;
import com.duotail.utils.email.mailhog.dto.MailhogReplyRequest;
import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/email", headers = "version=1")
public class MailhogController {

    private static final MediaType MESSAGE_RFC822 = MediaType.parseMediaType("message/rfc822");

    private final MailhogService mailhogService;
    private final EmlContentExtractor emlContentExtractor;
    private final MailhogReplyService mailhogReplyService;

    public MailhogController(MailhogService mailhogService,
                             EmlContentExtractor emlContentExtractor,
                             MailhogReplyService mailhogReplyService) {
        this.mailhogService = mailhogService;
        this.emlContentExtractor = emlContentExtractor;
        this.mailhogReplyService = mailhogReplyService;
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

    @GetMapping("/messages/{id}/content")
    public ResponseEntity<EmailContent> getMessageContent(@PathVariable String id) {
        return ResponseEntity.ok(emlContentExtractor.extract(id, mailhogService.getMessageEml(id)));
    }

    /**
     * No {@code produces} on purpose: it would make the JSON error bodies from
     * {@link MailhogExceptionHandler} un-writable for a client sending {@code Accept: message/rfc822}.
     */
    @GetMapping("/messages/{id}/eml")
    public ResponseEntity<byte[]> downloadMessageEml(@PathVariable String id) {
        var eml = mailhogService.getMessageEml(id);
        return ResponseEntity.ok()
                .contentType(MESSAGE_RFC822)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(id + ".eml").build().toString())
                .body(eml);
    }

    @PostMapping("/messages/{id}/reply")
    public void reply(@PathVariable String id, @Valid @RequestBody MailhogReplyRequest request)
            throws MessagingException, PermissionException {
        mailhogReplyService.reply(id, request, false);
    }

    @PostMapping("/messages/{id}/reply-all")
    public void replyAll(@PathVariable String id, @Valid @RequestBody MailhogReplyRequest request)
            throws MessagingException, PermissionException {
        mailhogReplyService.reply(id, request, true);
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

package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/bounce", headers = "version=1")
public class BounceEmailController {

    private final BounceEmailService bounceEmailService;

    public BounceEmailController(BounceEmailService bounceEmailService) {
        this.bounceEmailService = bounceEmailService;
    }

    @PostMapping
    public void sendBounce(@Valid @RequestBody BounceRequest bounceRequest) throws MessagingException, PermissionException {
        bounceEmailService.sendBounce(bounceRequest);
    }
}

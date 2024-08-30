package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSendServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private EmailSendService emailSendService;

    @Test
    void sendEmail() throws MessagingException {
        var emailRequest = new EmailRequest();
        emailRequest.setFrom("fn ln <fn.ln@whatever.com>");
        emailRequest.setTo(Set.of("abc <fsf@abc.com>", "ppp@ppp.con"));
        emailRequest.setCc(Set.of("ffw@fidsf.com"));
        emailRequest.setBcc(Set.of("bcc1@bfds.com"));
        emailRequest.setSubject("subject");
        emailRequest.setContent("content");

        try(MockedConstruction<MimeMessageHelper> mocked = mockConstruction(MimeMessageHelper.class)) {
            emailSendService.sendEmail(emailRequest);
            MimeMessageHelper mockHelper = mocked.constructed().get(0);
            verify(mockHelper).setFrom("fn ln <fn.ln@whatever.com>");
            verify(mockHelper).setTo(any(String[].class));
            verify(mockHelper).setCc(any(String[].class));
            verify(mockHelper).setBcc(any(String[].class));
            verify(mockHelper).setSubject("subject");
            verify(mockHelper).setText("content");
            verify(javaMailSender).send(any(MimeMessage.class));
        }

    }
}
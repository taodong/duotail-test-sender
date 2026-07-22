package com.duotail.utils.email.sender.config;

import com.duotail.utils.email.sender.permission.PermissionException;
import com.duotail.utils.email.sender.permission.SenderPermission;
import com.duotail.utils.email.sender.permission.SenderPermissionProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AppConfigTest {

    @Test
    void senderPermissionDelegatesToProcessor() throws PermissionException {
        AppConfig appConfig = new AppConfig();
        SenderPermissionProcessor processor = Mockito.mock(SenderPermissionProcessor.class);
        SenderPermission expected = Mockito.mock(SenderPermission.class);

        Mockito.when(processor.load("classpath:permissions-wildcard.yaml")).thenReturn(expected);

        SenderPermission actual = appConfig.senderPermission(processor, "classpath:permissions-wildcard.yaml");

        assertSame(expected, actual);
        Mockito.verify(processor).load("classpath:permissions-wildcard.yaml");
    }

    @Test
    void bounceMailSenderMirrorsHostAndSetsNullReversePath() {
        var mailProperties = new MailProperties();
        mailProperties.setHost("smtp.duotail.test");
        mailProperties.setPort(2525);
        mailProperties.getProperties().put("mail.smtp.auth", "false");

        JavaMailSenderImpl bounceSender =
                (JavaMailSenderImpl) new AppConfig().bounceMailSender(mailProperties, "<>");

        assertEquals("smtp.duotail.test", bounceSender.getHost());
        assertEquals(2525, bounceSender.getPort());
        assertEquals("<>", bounceSender.getJavaMailProperties().get("mail.smtp.from"));
        // existing JavaMail properties are carried over
        assertEquals("false", bounceSender.getJavaMailProperties().get("mail.smtp.auth"));
    }

    @Test
    void primaryMailSenderDoesNotSetReversePath() {
        var mailProperties = new MailProperties();
        mailProperties.setHost("smtp.duotail.test");

        JavaMailSenderImpl sender = (JavaMailSenderImpl) new AppConfig().mailSender(mailProperties);

        assertEquals("smtp.duotail.test", sender.getHost());
        assertNull(sender.getJavaMailProperties().get("mail.smtp.from"));
    }
}


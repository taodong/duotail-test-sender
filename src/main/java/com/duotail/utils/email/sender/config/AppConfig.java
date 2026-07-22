package com.duotail.utils.email.sender.config;

import com.duotail.utils.email.sender.permission.PermissionException;
import com.duotail.utils.email.sender.permission.SenderPermission;
import com.duotail.utils.email.sender.permission.SenderPermissionProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class AppConfig {

    @Bean
    public SenderPermission senderPermission(SenderPermissionProcessor processor,
                                             @Value("${app.permissions}") String permissionsLocation) throws PermissionException {
        return processor.load(permissionsLocation);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Primary {@link JavaMailSender} for normal mail, built from {@code spring.mail.*}. Declaring our
     * own sender backs off Spring Boot's autoconfigured {@code mailSender}, so we recreate it here and
     * mark it {@link Primary}. Used for everything except mocked bounces.
     */
    @Bean
    @Primary
    public JavaMailSender mailSender(MailProperties mailProperties) {
        return buildMailSender(mailProperties, null);
    }

    /**
     * Dedicated {@link JavaMailSender} for mocked bounces. Identical to {@link #mailSender} but sets
     * the SMTP {@code mail.smtp.from} envelope reverse-path (default {@code <>}, the null sender) so
     * generated DSNs are recognized as bounces by RFC-compliant classifiers such as Haraka's
     * {@code bounce} plugin.
     */
    @Bean
    public JavaMailSender bounceMailSender(MailProperties mailProperties,
                                           @Value("${app.bounce.reverse-path}") String reversePath) {
        return buildMailSender(mailProperties, reversePath);
    }

    private JavaMailSender buildMailSender(MailProperties mailProperties, String reversePath) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getHost());
        if (mailProperties.getPort() != null) {
            sender.setPort(mailProperties.getPort());
        }
        sender.setUsername(mailProperties.getUsername());
        sender.setPassword(mailProperties.getPassword());
        sender.setProtocol(mailProperties.getProtocol());
        if (mailProperties.getDefaultEncoding() != null) {
            sender.setDefaultEncoding(mailProperties.getDefaultEncoding().name());
        }

        var properties = new Properties();
        properties.putAll(mailProperties.getProperties());
        if (reversePath != null) {
            properties.put("mail.smtp.from", reversePath);
        }
        sender.setJavaMailProperties(properties);
        return sender;
    }
}


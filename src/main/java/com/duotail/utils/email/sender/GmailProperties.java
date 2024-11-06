package com.duotail.utils.email.sender;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.gmail")
@Data
public class GmailProperties {
    private String username;
    private String password;
    private String host;
    private String port;
}

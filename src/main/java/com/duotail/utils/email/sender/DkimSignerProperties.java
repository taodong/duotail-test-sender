package com.duotail.utils.email.sender;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.dkim")
@Data
public class DkimSignerProperties {
    private boolean enabled;
    private String privateKeyPath;
    private String selector;
    private String domain;
    private String bodyCanonicalization;
    private String headerCanonicalization;
}

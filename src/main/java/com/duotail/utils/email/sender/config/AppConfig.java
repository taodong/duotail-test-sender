package com.duotail.utils.email.sender.config;

import com.duotail.utils.email.sender.permission.PermissionException;
import com.duotail.utils.email.sender.permission.SenderPermission;
import com.duotail.utils.email.sender.permission.SenderPermissionProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public SenderPermission senderPermission(SenderPermissionProcessor processor,
                                             @Value("${app.permissions}") String permissionsLocation) throws PermissionException {
        return processor.load(permissionsLocation);
    }
}


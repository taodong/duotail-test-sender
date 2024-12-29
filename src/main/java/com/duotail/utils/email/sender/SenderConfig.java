package com.duotail.utils.email.sender;

import io.github.taodong.mail.dkim.DkimMimeMessageHelper;
import io.github.taodong.mail.dkim.DkimSigningService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("unused")
@Configuration
public class SenderConfig {

    @Bean
    DkimSigningService dkimSigningService() {
        return new DkimSigningService();
    }

    @Bean
    DkimMimeMessageHelper dkimMimeMessageHelper() {
        return new DkimMimeMessageHelper();
    }
}

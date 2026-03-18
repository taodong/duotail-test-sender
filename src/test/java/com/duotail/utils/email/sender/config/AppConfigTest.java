package com.duotail.utils.email.sender.config;

import com.duotail.utils.email.sender.permission.PermissionException;
import com.duotail.utils.email.sender.permission.SenderPermission;
import com.duotail.utils.email.sender.permission.SenderPermissionProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
}


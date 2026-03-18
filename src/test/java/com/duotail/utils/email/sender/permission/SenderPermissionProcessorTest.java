package com.duotail.utils.email.sender.permission;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenderPermissionProcessorTest {

    private final SenderPermissionProcessor processor = new SenderPermissionProcessor(new DefaultResourceLoader());

    @Test
    void loadWithWildcardPolicySetsAllowAllFlags() throws PermissionException {
        SenderPermission permission = processor.load("classpath:permissions-wildcard.yaml");

        assertTrue(permission.from().allowAllDomains());
        assertTrue(permission.from().allowAllEmails());
        assertTrue(permission.to().allowAllDomains());
        assertTrue(permission.to().allowAllEmails());
        assertEquals(250, permission.batchSize());
        assertTrue(permission.from().allowedDomains().isEmpty());
        assertTrue(permission.from().allowedEmails().isEmpty());
    }

    @Test
    void loadIgnoresInvalidEmailsAndDomains() throws PermissionException {
        SenderPermission permission = processor.load("classpath:permissions-invalid-values.yaml");

        assertFalse(permission.from().allowAllDomains());
        assertEquals(1, permission.from().allowedDomains().size());
        assertEquals("allowed.example.com", permission.from().allowedDomains().getFirst());

        assertFalse(permission.to().allowAllEmails());
        assertEquals(1, permission.to().allowedEmails().size());
        assertEquals("allowed@example.com", permission.to().allowedEmails().getFirst());

        assertFalse(permission.to().allowAllDomains());
        assertEquals(1, permission.to().allowedDomains().size());
        assertEquals("to.example.com", permission.to().allowedDomains().getFirst());
        assertEquals(10_000, permission.batchSize());
    }

    @Test
    void loadThrowsWhenFileDoesNotExist() {
        assertThrows(PermissionException.class, () -> processor.load("classpath:not-found-permissions.yaml"));
    }
}



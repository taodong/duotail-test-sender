package com.duotail.utils.email.sender.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactPermissionTest {

    @Test
    void hasPermissionReturnsTrueWhenAllDomainsAllowed() throws PermissionException {
        ContactPermission permission = new ContactPermission(true, List.of(), List.of());

        assertTrue(permission.hasPermission("user@any-domain.example"));
    }

    @Test
    void hasPermissionMatchesExactDomain() throws PermissionException {
        ContactPermission permission = new ContactPermission(false, List.of("example.com"), List.of());

        assertTrue(permission.hasPermission("user@example.com"));
    }

    @Test
    void hasPermissionMatchesSubdomain() throws PermissionException {
        ContactPermission permission = new ContactPermission(false, List.of("example.com"), List.of());

        assertTrue(permission.hasPermission("user@dev.mail.example.com"));
    }

    @Test
    void hasPermissionReturnsFalseWhenDomainListDefinedButNoMatch() throws PermissionException {
        ContactPermission permission = new ContactPermission(false, List.of("example.com"), List.of("user@allowed.test"));

        assertFalse(permission.hasPermission("user@another.com"));
    }

    @Test
    void hasPermissionChecksEmailListWhenDomainsAreNotDefined() throws PermissionException {
        ContactPermission permission = new ContactPermission(false, List.of(), List.of("allowed@example.com"));

        assertTrue(permission.hasPermission("ALLOWED@example.com"));
    }

    @Test
    void hasPermissionReturnsFalseWhenEmailListDoesNotMatch() throws PermissionException {
        ContactPermission permission = new ContactPermission(false, List.of(), List.of("allowed@example.com"));

        assertFalse(permission.hasPermission("denied@example.com"));
    }

    @Test
    void hasPermissionThrowsWhenNoPoliciesAreDefined() {
        ContactPermission permission = new ContactPermission(false, List.of(), List.of());

        PermissionException exception = assertThrows(PermissionException.class,
                () -> permission.hasPermission("any@example.com"));
        assertEquals("No permissions defined for sending emails.", exception.getMessage());
    }
}


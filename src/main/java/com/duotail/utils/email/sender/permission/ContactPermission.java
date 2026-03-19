package com.duotail.utils.email.sender.permission;

import java.util.List;
import java.util.Locale;

public record ContactPermission(boolean allowAllDomains,
                                List<String> allowedDomains,
                                List<String> allowedEmails) {

    public ContactPermission {
        allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
        allowedEmails = allowedEmails == null ? List.of() : List.copyOf(allowedEmails);
    }

    public boolean hasPermission(String email) throws PermissionException {
        if (allowAllDomains) {
            return true;
        }

        if (!allowedDomains.isEmpty()) {
            String emailDomain = extractDomain(email);
            if (emailDomain == null) {
                return false;
            }

            String normalizedEmailDomain = emailDomain.toLowerCase(Locale.ROOT);
            for (String allowedDomain : allowedDomains) {
                String normalizedAllowedDomain = allowedDomain.toLowerCase(Locale.ROOT);
                if (normalizedEmailDomain.equals(normalizedAllowedDomain)
                        || normalizedEmailDomain.endsWith("." + normalizedAllowedDomain)) {
                    return true;
                }
            }

            return false;
        }

        if (!allowedEmails.isEmpty()) {
            String normalizedEmail = normalize(email);
            if (normalizedEmail == null) {
                return false;
            }
            for (String allowedEmail : allowedEmails) {
                if (normalizedEmail.equals(normalize(allowedEmail))) {
                    return true;
                }
            }
            return false;
        }

        throw new PermissionException("No permissions defined for sending emails.");
    }

    private String extractDomain(String email) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail == null) {
            return null;
        }

        int separatorIndex = normalizedEmail.lastIndexOf('@');
        if (separatorIndex <= 0 || separatorIndex == normalizedEmail.length() - 1) {
            return null;
        }
        return normalizedEmail.substring(separatorIndex + 1);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}

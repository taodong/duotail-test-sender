package com.duotail.utils.email.sender.permission;

import java.util.List;

public record ContactPermission(boolean allowAllDomains,
                                List<String> allowedDomains,
                                boolean allowAllEmails,
                                List<String> allowedEmails) {
}

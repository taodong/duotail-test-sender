package com.duotail.utils.email.sender.permission;

import java.util.List;

public record ContactPermission(boolean allDomainsAllowed,
                                List<String> whitelistedDomains,
                                boolean allEmailsAllowed,
                                List<String> whitelistedEmails) {
}

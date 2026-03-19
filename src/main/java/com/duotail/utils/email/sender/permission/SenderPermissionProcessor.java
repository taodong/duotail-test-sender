package com.duotail.utils.email.sender.permission;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SenderPermissionProcessor {

    private static final int DEFAULT_BATCH_SIZE = 10_000;

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    );

    private final ResourceLoader resourceLoader;

    public SenderPermissionProcessor(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public SenderPermission load(String location) throws PermissionException {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new PermissionException("Permission file not found: " + location);
        }

        try (var inputStream = resource.getInputStream()) {
            Object loaded = new Yaml().load(inputStream);
            if (!(loaded instanceof Map<?, ?> root)) {
                throw new PermissionException("Invalid permissions format in " + location);
            }

            ContactPermission from = parseContactPermission(root.get("from"), "from");
            ContactPermission to = parseContactPermission(root.get("to"), "to");
            int batchSize = parseBatchSize(root.get("batchSize"));

            return new SenderPermission(from, to, batchSize);
        } catch (IOException | YAMLException e) {
            throw new PermissionException("Failed to parse permission file: " + location, e);
        }
    }

    private ContactPermission parseContactPermission(Object policyValue, String fieldName) {
        if (!(policyValue instanceof Map<?, ?> policyMap)) {
            return new ContactPermission(false, List.of(), List.of());
        }

        List<String> domains = toStringList(policyMap.get("domains"));
        boolean allowAllDomains = domains.contains("*");
        List<String> allowedDomains = allowAllDomains
                ? List.of()
                : filterValidEntries(domains, this::isValidDomain, fieldName + ".domains");

        List<String> emails = toStringList(policyMap.get("emails"));
        List<String> allowedEmails = filterValidEntries(emails, this::isValidEmail, fieldName + ".emails");

        return new ContactPermission(allowAllDomains, allowedDomains, allowedEmails);
    }

    private int parseBatchSize(Object batchSizeValue) {
        if (batchSizeValue instanceof Number numberValue) {
            int batchSize = numberValue.intValue();
            return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        }
        if (batchSizeValue instanceof String stringValue) {
            try {
                int batchSize = Integer.parseInt(stringValue);
                return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
            } catch (NumberFormatException ignored) {
                return DEFAULT_BATCH_SIZE;
            }
        }
        return DEFAULT_BATCH_SIZE;
    }

    private List<String> toStringList(Object values) {
        if (!(values instanceof List<?> listValue)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Object value : listValue) {
            if (value instanceof String stringValue) {
                result.add(stringValue.trim());
            }
        }
        return result;
    }

    private List<String> filterValidEntries(List<String> values, Predicate<String> validator, String fieldName) {
        List<String> validEntries = new ArrayList<>();
        for (String value : values) {
            if ("*".equals(value)) {
                continue;
            }
            if (validator.test(value)) {
                validEntries.add(value);
            } else {
                LOG.warn("Ignored invalid entry '{}' in {}", value, fieldName);
            }
        }
        return List.copyOf(validEntries);
    }

    private boolean isValidDomain(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT);
        return DOMAIN_PATTERN.matcher(normalized).matches();
    }

    private boolean isValidEmail(String email) {
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }
}



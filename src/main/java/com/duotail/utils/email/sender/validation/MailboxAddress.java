package com.duotail.utils.email.sender.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A single RFC 5322 mailbox: either a bare {@code local@domain} address or {@code Name <local@domain>}.
 * {@code null} is valid; combine with {@code @NotBlank} when the value is required.
 */
@Documented
@Constraint(validatedBy = MailboxAddressValidator.class)
@Target({FIELD, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface MailboxAddress {

    String message() default "must be an email address or \"Name <address>\"";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

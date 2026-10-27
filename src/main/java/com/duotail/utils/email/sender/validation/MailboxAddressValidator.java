package com.duotail.utils.email.sender.validation;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MailboxAddressValidator implements ConstraintValidator<MailboxAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            InternetAddress[] parsed = InternetAddress.parse(value, true);
            // Exactly one mailbox: rejects comma lists (including an unquoted comma in the name) and RFC 822 groups
            if (parsed.length != 1 || parsed[0].isGroup()) {
                return false;
            }
            // parse() alone allows a bare local name with no @domain
            parsed[0].validate();
            return true;
        } catch (AddressException ex) {
            return false;
        }
    }
}

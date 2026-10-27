package com.duotail.utils.email.sender.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailboxAddressValidatorTest {

    private final MailboxAddressValidator validator = new MailboxAddressValidator();

    @Test
    void nullIsValid() {
        assertTrue(validator.isValid(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "bounce-34352353@concreteabc.com",
            "Landscape Guy<bounce-34352353@concreteabc.com>",
            "Landscape Guy <bounce-34352353@concreteabc.com>",
            "United Airlines<bounces+347284-7acd-tao.dong_united=duotail.com@em7341.united.com>",
            "\"Doe, John\" <j@example.com>",
            "<a@example.com>",
            "Jöhn <j@example.com>",
            "=?UTF-8?B?SsO2aG4=?= <j@example.com>"
    })
    void acceptsBareAndDisplayNameAddresses(String value) {
        assertTrue(validator.isValid(value, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "bob",
            "Bob <bob>",
            "a@example.com, b@example.com",
            "Doe, John <j@example.com>",
            "Group: a@example.com, b@example.com;",
            "Name a@example.com",
            "a@",
            "@example.com",
            "a@example.com\r\nBcc: evil@example.com"
    })
    void rejectsMalformedAddresses(String value) {
        assertFalse(validator.isValid(value, null));
    }
}

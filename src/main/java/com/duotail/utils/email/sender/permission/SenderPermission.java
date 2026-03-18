package com.duotail.utils.email.sender.permission;

public record SenderPermission(ContactPermission from,
                               ContactPermission to,
                               int batchSize) {
}

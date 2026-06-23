package io.github.commonpay.util;

import java.util.UUID;

/** Generates database-safe unique identifiers without relying on a host platform utility. */
public final class PayIdGenerator {

    private PayIdGenerator() {
    }

    public static String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

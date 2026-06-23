package io.github.commonpay.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PayIdGeneratorTest {

    @Test
    void generatesUniqueDatabaseSafeIdentifiers() {
        Set<String> identifiers = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String identifier = PayIdGenerator.nextId();
            assertEquals(32, identifier.length());
            assertFalse(identifier.contains("-"));
            identifiers.add(identifier);
        }

        assertEquals(1000, identifiers.size());
    }
}

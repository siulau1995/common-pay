package io.github.commonpay.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayApiResponseTest {

    @Test
    void createsSuccessfulResponse() {
        PayApiResponse<String> response = PayApiResponse.success("created");

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("created", response.getData());
    }
}

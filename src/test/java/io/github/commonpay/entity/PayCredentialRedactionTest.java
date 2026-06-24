package io.github.commonpay.entity;

import io.github.commonpay.model.PayChannelRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PayCredentialRedactionTest {

    @Test
    void excludesProviderSecretsFromGeneratedToStringMethods() {
        PayChannelConfigEntity config = new PayChannelConfigEntity();
        config.setConfigJson("{\"appPrivateKey\":\"TOP-SECRET\"}");
        PayChannelRequest request = new PayChannelRequest();
        request.setChannelConfig(config);

        assertFalse(config.toString().contains("TOP-SECRET"));
        assertFalse(request.toString().contains("TOP-SECRET"));
    }
}

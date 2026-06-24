package io.github.commonpay.channel.impl;

import com.alipay.api.internal.util.AlipaySignature;
import io.github.commonpay.entity.PayChannelConfigEntity;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayNotifyMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlipayPayChannelAdapterSecurityTest {

    private static final String APP_ID = "test-app-id";
    private static final String MERCHANT_ID = "test-seller-id";
    private static String privateKey;
    private static String publicKey;

    private final AlipayPayChannelAdapter adapter = new AlipayPayChannelAdapter();

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Test
    void acceptsValidSignedCallbackForConfiguredApplicationAndMerchant() throws Exception {
        PayNotifyMessage message = adapter.parsePayNotify(signedRequest(APP_ID, MERCHANT_ID, "12.34"));

        assertTrue(message.isVerifySuccess());
    }

    @Test
    void rejectsCallbackWhoseAmountWasChangedAfterSigning() throws Exception {
        PayChannelRequest request = signedRequest(APP_ID, MERCHANT_ID, "12.34");
        request.getParams().put("total_amount", "99.99");

        assertFalse(adapter.parsePayNotify(request).isVerifySuccess());
    }

    @Test
    void rejectsSignedCallbackFromDifferentApplication() throws Exception {
        PayChannelRequest request = signedRequest("another-app", MERCHANT_ID, "12.34");

        assertFalse(adapter.parsePayNotify(request).isVerifySuccess());
    }

    @Test
    void rejectsSignedCallbackFromDifferentMerchant() throws Exception {
        PayChannelRequest request = signedRequest(APP_ID, "another-seller", "12.34");

        assertFalse(adapter.parsePayNotify(request).isVerifySuccess());
    }

    private PayChannelRequest signedRequest(String appId, String sellerId, String amount) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", appId);
        params.put("seller_id", sellerId);
        params.put("out_trade_no", "PAY-SECURITY-TEST");
        params.put("trade_no", "CHANNEL-1001");
        params.put("total_amount", amount);
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("sign_type", "RSA2");
        String content = AlipaySignature.getSignCheckContentV1(params);
        params.put("sign", AlipaySignature.rsa256Sign(content, privateKey, "UTF-8"));

        PayChannelConfigEntity config = new PayChannelConfigEntity();
        config.setAppId(APP_ID);
        config.setMerchantId(MERCHANT_ID);
        config.setConfigJson("{\"alipayPublicKey\":\"" + publicKey
                + "\",\"charset\":\"UTF-8\",\"signType\":\"RSA2\"}");

        PayChannelRequest request = new PayChannelRequest();
        request.setChannelConfig(config);
        request.setParams(params);
        return request;
    }
}

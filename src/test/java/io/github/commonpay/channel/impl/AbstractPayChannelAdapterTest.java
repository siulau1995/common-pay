package io.github.commonpay.channel.impl;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayNotifyMessage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractPayChannelAdapterTest {

    private final TestAdapter adapter = new TestAdapter();

    @Test
    void convertsDecimalCurrencyAmountToCents() {
        PayChannelRequest request = new PayChannelRequest();
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "PAY-1001");
        params.put("total_amount", "12.34");
        request.setParams(params);

        PayNotifyMessage message = adapter.parsePayNotify(request);

        assertEquals("PAY-1001", message.getPayOrderNo());
        assertEquals(Long.valueOf(1234L), message.getAmount());
    }

    @Test
    void preservesIntegerMinorUnitsFromJsonCallbacks() {
        PayChannelRequest request = new PayChannelRequest();
        request.setRawBody("{\"out_trade_no\":\"PAY-1002\",\"amount\":{\"total\":321}}");

        PayNotifyMessage message = adapter.parseRefundNotify(request);

        assertEquals("PAY-1002", message.getPayOrderNo());
        assertEquals(Long.valueOf(321L), message.getAmount());
    }

    @Test
    void rejectsCurrencyAmountsWithUnsupportedPrecision() {
        PayChannelRequest request = new PayChannelRequest();
        Map<String, String> params = new HashMap<>();
        params.put("total_amount", "1.001");
        request.setParams(params);

        assertThrows(ArithmeticException.class, () -> adapter.parsePayNotify(request));
    }

    private static class TestAdapter extends AbstractPayChannelAdapter {

        @Override
        public PayChannelCodeEnum getChannelCode() {
            return PayChannelCodeEnum.ALIPAY;
        }

        @Override
        public PayNotifyMessage parsePayNotify(PayChannelRequest request) {
            return buildMessageFromParams(request, "out_trade_no", "total_amount");
        }

        @Override
        public PayNotifyMessage parseRefundNotify(PayChannelRequest request) {
            return buildMessageFromJson(request, "out_trade_no", "total");
        }

        @Override
        public String buildPayNotifySuccessResponse() {
            return "success";
        }

        @Override
        public String buildPayNotifyFailResponse(String message) {
            return "fail";
        }

        @Override
        public String buildRefundNotifySuccessResponse() {
            return "success";
        }

        @Override
        public String buildRefundNotifyFailResponse(String message) {
            return "fail";
        }
    }
}

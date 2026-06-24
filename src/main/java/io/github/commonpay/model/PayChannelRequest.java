package io.github.commonpay.model;

import io.github.commonpay.entity.PayOrderEntity;
import io.github.commonpay.entity.PayRefundOrderEntity;
import io.github.commonpay.entity.PayChannelConfigEntity;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
public class PayChannelRequest {
    private String tenantId;
    private String payAppCode;
    @ToString.Exclude
    private PayChannelConfigEntity channelConfig;
    private PayOrderEntity order;
    private List<PayCreateItemRequest> items;
    private PayRefundOrderEntity refundOrder;
    private String returnUrl;
    private Map<String, String> params;
    private Map<String, String> headers;
    private String rawBody;
}

package io.github.commonpay.model;

import io.github.commonpay.enums.PayChannelCodeEnum;
import lombok.Data;

import java.util.List;

@Data
public class PayRefundRequest {
    private String payOrderNo;
    /** Optional idempotency key. Reusing it returns the existing refund for the same payment order. */
    private String refundNo;
    private String bizType;
    private String refTable;
    private String refValue;
    private String refNo;
    private PayChannelCodeEnum channelCode;
    private String payAppCode;
    private Long refundAmount;
    private String refundReason;
    private String extraJson;
    private List<PayRefundItemRequest> items;
}

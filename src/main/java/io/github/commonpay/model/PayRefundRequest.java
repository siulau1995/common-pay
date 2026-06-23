package io.github.commonpay.model;

import io.github.commonpay.enums.PayChannelCodeEnum;
import lombok.Data;

import java.util.List;

@Data
public class PayRefundRequest {
    private String payOrderNo;
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

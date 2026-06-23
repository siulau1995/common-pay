package io.github.commonpay.model;

import lombok.Data;

@Data
public class PayRefundItemRequest {
    private String payOrderItemId;
    private String refTable;
    private String refValue;
    private String refNo;
    private Long refundAmount;
}

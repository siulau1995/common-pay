package io.github.commonpay.model;

import lombok.Data;

@Data
public class PayCreateItemRequest {
    private String bizType;
    private String refTable;
    private String refValue;
    private String refNo;
    private String itemName;
    private Long itemAmount;
}

package io.github.commonpay.model;

import lombok.Data;

@Data
public class PayClosedContext {
    private String payOrderNo;
    private String bizType;
    private String refTable;
    private String refValue;
    private String refNo;
    private String closeReason;
}

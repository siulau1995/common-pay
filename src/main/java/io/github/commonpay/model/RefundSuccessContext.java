package io.github.commonpay.model;

import lombok.Data;

import java.util.Date;

@Data
public class RefundSuccessContext {
    private String refundNo;
    private String payOrderNo;
    private String channelCode;
    private String channelRefundNo;
    private String bizType;
    private String refTable;
    private String refValue;
    private String refNo;
    private Long refundAmount;
    private Date refundTime;
}

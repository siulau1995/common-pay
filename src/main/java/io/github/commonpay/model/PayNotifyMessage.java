package io.github.commonpay.model;

import lombok.Data;

import java.util.Date;

@Data
public class PayNotifyMessage {
    private String payOrderNo;
    private String refundNo;
    private String channelNotifyId;
    private String channelTradeNo;
    private String channelRefundNo;
    private Long amount;
    private Date notifyTime;
    private Date successTime;
    private boolean success;
    private boolean verifySuccess;
    private String rawBody;
}

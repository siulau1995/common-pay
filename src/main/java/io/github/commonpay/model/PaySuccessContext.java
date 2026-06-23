package io.github.commonpay.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class PaySuccessContext {
    private String payOrderNo;
    private String channelCode;
    private String channelTradeNo;
    private String bizType;
    private String refTable;
    private String refValue;
    private String refNo;
    private Long paidAmount;
    private Date payTime;
    private List<PayCreateItemRequest> items;
}

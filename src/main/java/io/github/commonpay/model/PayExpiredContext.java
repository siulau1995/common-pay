package io.github.commonpay.model;

import lombok.Data;

import java.util.Date;

@Data
public class PayExpiredContext {
    private String payOrderNo;
    private String bizType;
    private String refTable;
    private String refValue;
    private String refNo;
    private Date expireTime;
}

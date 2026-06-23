package io.github.commonpay.model;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.enums.PaySceneEnum;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class PayCreateRequest {
    private String tenantId;
    /**
     * 可选。业务方需要在创建支付前生成支付链接时，可指定支付单号。
     * 不传则由 common-pay 自动生成。
     */
    private String payOrderNo;
    private String bizType;
    private String refTable;
    private String refValue;
    private String refNo;
    private PayChannelCodeEnum channelCode;
    private PaySceneEnum payScene;
    private String payAppCode;
    private String subject;
    private String body;
    private Long totalAmount;
    private Date expireTime;
    private String clientIp;
    private String openId;
    /**
     * 可选。支付宝 PAGE/WAP 等网页支付完成后的前端回跳地址。
     * 不传则使用支付渠道配置中的 returnUrl。
     */
    private String returnUrl;
    private String extraJson;
    private List<PayCreateItemRequest> items;
}

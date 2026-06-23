package io.github.commonpay.model;

import io.github.commonpay.enums.PayChannelCodeEnum;
import lombok.Data;

@Data
public class PayQueryRequest {
    private String payOrderNo;
    private PayChannelCodeEnum channelCode;
    private String payAppCode;
}

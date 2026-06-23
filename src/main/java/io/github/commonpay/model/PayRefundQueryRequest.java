package io.github.commonpay.model;

import io.github.commonpay.enums.PayChannelCodeEnum;
import lombok.Data;

@Data
public class PayRefundQueryRequest {
    private String refundNo;
    private PayChannelCodeEnum channelCode;
    private String payAppCode;
}

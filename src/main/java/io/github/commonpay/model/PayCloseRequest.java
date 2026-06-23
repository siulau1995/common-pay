package io.github.commonpay.model;

import io.github.commonpay.enums.PayChannelCodeEnum;
import lombok.Data;

@Data
public class PayCloseRequest {
    private String payOrderNo;
    private PayChannelCodeEnum channelCode;
    private String payAppCode;
    private String closeReason;
}

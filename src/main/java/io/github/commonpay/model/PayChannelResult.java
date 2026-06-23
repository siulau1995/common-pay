package io.github.commonpay.model;

import io.github.commonpay.enums.PayOrderStatusEnum;
import io.github.commonpay.enums.PayRefundStatusEnum;
import lombok.Data;

import java.util.Date;

@Data
public class PayChannelResult {
    private boolean success;
    private PayOrderStatusEnum orderStatus;
    private PayRefundStatusEnum refundStatus;
    private Long amount;
    private String channelTradeNo;
    private String channelRefundNo;
    private Date successTime;
    private Date closeTime;
    private String rawResponse;
    private String errorCode;
    private String errorMessage;

    public static PayChannelResult success(String rawResponse) {
        PayChannelResult result = new PayChannelResult();
        result.setSuccess(true);
        result.setRawResponse(rawResponse);
        return result;
    }

    public static PayChannelResult fail(String errorMessage) {
        PayChannelResult result = new PayChannelResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}

package io.github.commonpay.channel;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayChannelResult;
import io.github.commonpay.model.PayCreateResult;
import io.github.commonpay.model.PayNotifyMessage;

public interface PayChannelAdapter {

    PayChannelCodeEnum getChannelCode();

    PayCreateResult prepay(PayChannelRequest request);

    PayChannelResult close(PayChannelRequest request);

    PayChannelResult refund(PayChannelRequest request);

    PayChannelResult query(PayChannelRequest request);

    PayChannelResult queryRefund(PayChannelRequest request);

    PayNotifyMessage parsePayNotify(PayChannelRequest request);

    PayNotifyMessage parseRefundNotify(PayChannelRequest request);

    String buildPayNotifySuccessResponse();

    String buildPayNotifyFailResponse(String message);

    String buildRefundNotifySuccessResponse();

    String buildRefundNotifyFailResponse(String message);
}

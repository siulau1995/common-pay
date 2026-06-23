package io.github.commonpay.channel.impl;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayNotifyMessage;
import org.springframework.stereotype.Component;

@Component
public class WechatPayChannelAdapter extends AbstractPayChannelAdapter {

    @Override
    public PayChannelCodeEnum getChannelCode() {
        return PayChannelCodeEnum.WECHAT;
    }

    @Override
    public PayNotifyMessage parsePayNotify(PayChannelRequest request) {
        return buildMessageFromJson(request, "out_trade_no", "total");
    }

    @Override
    public PayNotifyMessage parseRefundNotify(PayChannelRequest request) {
        return buildMessageFromJson(request, "out_trade_no", "refund");
    }

    @Override
    public String buildPayNotifySuccessResponse() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    @Override
    public String buildPayNotifyFailResponse(String message) {
        return "{\"code\":\"FAIL\",\"message\":\"" + escape(message) + "\"}";
    }

    @Override
    public String buildRefundNotifySuccessResponse() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    @Override
    public String buildRefundNotifyFailResponse(String message) {
        return "{\"code\":\"FAIL\",\"message\":\"" + escape(message) + "\"}";
    }

    private String escape(String message) {
        return message == null ? "" : message.replace("\"", "'");
    }
}

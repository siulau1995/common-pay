package io.github.commonpay.channel.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.commonpay.channel.PayChannelAdapter;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayChannelResult;
import io.github.commonpay.model.PayCreateResult;
import io.github.commonpay.model.PayNotifyMessage;
import io.github.commonpay.util.PayException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;

public abstract class AbstractPayChannelAdapter implements PayChannelAdapter {

    @Override
    public PayCreateResult prepay(PayChannelRequest request) {
        throw unsupported("prepay");
    }

    @Override
    public PayChannelResult close(PayChannelRequest request) {
        throw unsupported("close");
    }

    @Override
    public PayChannelResult refund(PayChannelRequest request) {
        throw unsupported("refund");
    }

    @Override
    public PayChannelResult query(PayChannelRequest request) {
        throw unsupported("query");
    }

    @Override
    public PayChannelResult queryRefund(PayChannelRequest request) {
        throw unsupported("queryRefund");
    }

    protected PayNotifyMessage buildMessageFromJson(PayChannelRequest request, String orderField, String amountField) {
        JSONObject json = parseJson(request.getRawBody());
        PayNotifyMessage message = new PayNotifyMessage();
        message.setPayOrderNo(json.getString(orderField));
        message.setRefundNo(json.getString("refund_no"));
        message.setChannelNotifyId(json.getString("id"));
        message.setChannelTradeNo(json.getString("transaction_id"));
        message.setChannelRefundNo(json.getString("channel_refund_no"));
        message.setAmount(jsonAmountToCent(json, amountField));
        message.setNotifyTime(new Date());
        message.setSuccess(true);
        message.setVerifySuccess(false);
        message.setRawBody(request.getRawBody());
        return message;
    }

    protected PayNotifyMessage buildMessageFromParams(PayChannelRequest request, String orderField, String amountField) {
        Map<String, String> params = request.getParams();
        PayNotifyMessage message = new PayNotifyMessage();
        message.setPayOrderNo(params.get(orderField));
        message.setRefundNo(params.get("out_request_no"));
        message.setChannelNotifyId(params.get("notify_id"));
        message.setChannelTradeNo(params.get("trade_no"));
        message.setChannelRefundNo(params.get("trade_no"));
        message.setAmount(yuanToCent(params.get(amountField)));
        message.setNotifyTime(new Date());
        message.setSuccess(true);
        message.setVerifySuccess(false);
        message.setRawBody(JSON.toJSONString(params));
        return message;
    }

    protected JSONObject parseJson(String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawBody);
    }

    protected Long jsonAmountToCent(JSONObject json, String amountField) {
        Object amount = json.get(amountField);
        if (amount == null && json.getJSONObject("amount") != null) {
            amount = json.getJSONObject("amount").get(amountField);
        }
        if (amount == null) {
            return null;
        }
        if (amount instanceof Number) {
            return new BigDecimal(String.valueOf(amount)).longValueExact();
        }
        return yuanToCent(String.valueOf(amount));
    }

    protected Long yuanToCent(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(amount.trim())
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
    }

    private PayException unsupported(String operation) {
        return new PayException("Payment channel " + getChannelCode() + " does not implement " + operation);
    }
}

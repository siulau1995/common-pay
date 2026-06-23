package io.github.commonpay.service;

import io.github.commonpay.entity.PayOrderEntity;
import io.github.commonpay.entity.PayRefundOrderEntity;
import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.model.PayCloseRequest;
import io.github.commonpay.model.PayCreateRequest;
import io.github.commonpay.model.PayCreateResult;
import io.github.commonpay.model.PayQueryRequest;
import io.github.commonpay.model.PayRefundQueryRequest;
import io.github.commonpay.model.PayRefundRequest;

import java.util.Map;

public interface CommonPayService {

    PayCreateResult createPayOrder(PayCreateRequest request);

    PayOrderEntity queryPayOrder(PayQueryRequest request);

    PayOrderEntity closePayOrder(PayCloseRequest request);

    PayRefundOrderEntity createRefund(PayRefundRequest request);

    PayRefundOrderEntity queryRefund(PayRefundQueryRequest request);

    String handlePayNotify(String tenantId, PayChannelCodeEnum channelCode, String payAppCode, Map<String, String> params, Map<String, String> headers, String rawBody);

    String handleRefundNotify(String tenantId, PayChannelCodeEnum channelCode, String payAppCode, Map<String, String> params, Map<String, String> headers, String rawBody);
}

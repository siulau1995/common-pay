package io.github.commonpay.service;

import io.github.commonpay.model.PayClosedContext;
import io.github.commonpay.model.PayExpiredContext;
import io.github.commonpay.model.PaySuccessContext;
import io.github.commonpay.model.RefundSuccessContext;

public interface PayBusinessHandler {

    boolean supports(String bizType);

    void onPaySuccess(PaySuccessContext context);

    void onPayClosed(PayClosedContext context);

    void onPayExpired(PayExpiredContext context);

    void onRefundSuccess(RefundSuccessContext context);
}

package io.github.commonpay.examples;

import io.github.commonpay.model.PayClosedContext;
import io.github.commonpay.model.PayExpiredContext;
import io.github.commonpay.model.PaySuccessContext;
import io.github.commonpay.model.RefundSuccessContext;
import io.github.commonpay.service.PayBusinessHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DemoBusinessHandler implements PayBusinessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DemoBusinessHandler.class);

    @Override
    public boolean supports(String bizType) {
        return "DEMO_ORDER".equals(bizType);
    }

    @Override
    public void onPaySuccess(PaySuccessContext context) {
        LOG.info("Demo order paid: refNo={}, amount={}", context.getRefNo(), context.getPaidAmount());
    }

    @Override
    public void onPayClosed(PayClosedContext context) {
        LOG.info("Demo order closed: refNo={}", context.getRefNo());
    }

    @Override
    public void onPayExpired(PayExpiredContext context) {
        LOG.info("Demo order expired: refNo={}", context.getRefNo());
    }

    @Override
    public void onRefundSuccess(RefundSuccessContext context) {
        LOG.info("Demo order refunded: refNo={}, amount={}", context.getRefNo(), context.getRefundAmount());
    }
}

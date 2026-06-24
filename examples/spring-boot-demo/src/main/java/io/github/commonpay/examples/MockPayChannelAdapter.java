package io.github.commonpay.examples;

import io.github.commonpay.channel.impl.AbstractPayChannelAdapter;
import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.enums.PayOrderStatusEnum;
import io.github.commonpay.enums.PayRefundStatusEnum;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayChannelResult;
import io.github.commonpay.model.PayCreateResult;
import io.github.commonpay.model.PayNotifyMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

/**
 * Synthetic provider used only by the runnable example.
 */
@Component
public class MockPayChannelAdapter extends AbstractPayChannelAdapter {

    private static final String DEMO_SIGNATURE = "demo-signature";

    @Override
    public PayChannelCodeEnum getChannelCode() {
        return PayChannelCodeEnum.WECHAT;
    }

    @Override
    public PayCreateResult prepay(PayChannelRequest request) {
        PayCreateResult result = new PayCreateResult();
        result.setPayOrderNo(request.getOrder().getPayOrderNo());
        result.setChannelCode(getChannelCode());
        result.setPayScene(request.getOrder().getPayScene() == null
                ? null : io.github.commonpay.enums.PaySceneEnum.valueOf(request.getOrder().getPayScene()));
        result.setCodeUrl("http://localhost:8080/demo-provider/pay/" + request.getOrder().getPayOrderNo());
        result.getPayParams().put("demoSignature", DEMO_SIGNATURE);
        return result;
    }

    @Override
    public PayChannelResult close(PayChannelRequest request) {
        PayChannelResult result = PayChannelResult.success("{\"demo\":\"closed\"}");
        result.setOrderStatus(PayOrderStatusEnum.CLOSED);
        result.setCloseTime(new Date());
        return result;
    }

    @Override
    public PayChannelResult refund(PayChannelRequest request) {
        PayChannelResult result = PayChannelResult.success("{\"demo\":\"refund accepted\"}");
        result.setRefundStatus(PayRefundStatusEnum.PROCESSING);
        return result;
    }

    @Override
    public PayChannelResult query(PayChannelRequest request) {
        PayChannelResult result = PayChannelResult.success("{\"demo\":\"query\"}");
        result.setOrderStatus(PayOrderStatusEnum.WAIT_PAY);
        return result;
    }

    @Override
    public PayChannelResult queryRefund(PayChannelRequest request) {
        PayChannelResult result = PayChannelResult.success("{\"demo\":\"refund query\"}");
        result.setRefundStatus(PayRefundStatusEnum.PROCESSING);
        return result;
    }

    @Override
    public PayNotifyMessage parsePayNotify(PayChannelRequest request) {
        PayNotifyMessage message = buildMessageFromParams(request, "out_trade_no", "total_amount");
        message.setVerifySuccess(signatureMatches(request));
        message.setSuccess("TRADE_SUCCESS".equals(request.getParams().get("trade_status")));
        message.setSuccessTime(new Date());
        return message;
    }

    @Override
    public PayNotifyMessage parseRefundNotify(PayChannelRequest request) {
        PayNotifyMessage message = buildMessageFromParams(request, "out_trade_no", "refund_amount");
        message.setVerifySuccess(signatureMatches(request));
        message.setSuccess("REFUND_SUCCESS".equals(request.getParams().get("refund_status")));
        message.setSuccessTime(new Date());
        return message;
    }

    @Override
    public String buildPayNotifySuccessResponse() {
        return "success";
    }

    @Override
    public String buildPayNotifyFailResponse(String message) {
        return "fail";
    }

    @Override
    public String buildRefundNotifySuccessResponse() {
        return "success";
    }

    @Override
    public String buildRefundNotifyFailResponse(String message) {
        return "fail";
    }

    private boolean signatureMatches(PayChannelRequest request) {
        String actual = request.getParams().get("demo_signature");
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                DEMO_SIGNATURE.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}

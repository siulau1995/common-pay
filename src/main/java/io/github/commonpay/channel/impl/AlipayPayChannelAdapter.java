package io.github.commonpay.channel.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import io.github.commonpay.entity.PayChannelConfigEntity;
import io.github.commonpay.entity.PayOrderEntity;
import io.github.commonpay.entity.PayRefundOrderEntity;
import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.enums.PayOrderStatusEnum;
import io.github.commonpay.enums.PayRefundStatusEnum;
import io.github.commonpay.enums.PaySceneEnum;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayChannelResult;
import io.github.commonpay.model.PayCreateResult;
import io.github.commonpay.model.PayNotifyMessage;
import io.github.commonpay.util.PayException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Component
public class AlipayPayChannelAdapter extends AbstractPayChannelAdapter {

    private static final String SUCCESS_CODE = "10000";

    @Override
    public PayChannelCodeEnum getChannelCode() {
        return PayChannelCodeEnum.ALIPAY;
    }

    @Override
    public PayCreateResult prepay(PayChannelRequest request) {
        PayOrderEntity order = request.getOrder();
        if (order == null) {
            throw new PayException("支付宝下单失败：支付订单不能为空");
        }
        PaySceneEnum scene = PaySceneEnum.valueOf(order.getPayScene());
        switch (scene) {
            case NATIVE:
                return createNativePay(request);
            case PAGE:
                return createPagePay(request);
            case H5:
                return createWapPay(request);
            case APP:
                return createAppPay(request);
            default:
                throw new PayException("支付宝暂不支持该支付场景：" + scene.name());
        }
    }

    @Override
    public PayChannelResult close(PayChannelRequest request) {
        try {
            AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
            closeRequest.setBizContent(baseBizContent(request.getOrder(), null).toJSONString());
            AlipayTradeCloseResponse response = buildClient(request).execute(closeRequest);
            return response.isSuccess() ? PayChannelResult.success(response.getBody()) : failResult(response);
        } catch (Exception e) {
            return PayChannelResult.fail(e.getMessage());
        }
    }

    @Override
    public PayChannelResult refund(PayChannelRequest request) {
        try {
            PayRefundOrderEntity refundOrder = request.getRefundOrder();
            if (refundOrder == null) {
                return PayChannelResult.fail("退款单不能为空");
            }
            JSONObject bizContent = baseBizContent(request.getOrder(), null);
            bizContent.put("refund_amount", centToYuan(refundOrder.getRefundAmount()));
            bizContent.put("out_request_no", refundOrder.getRefundNo());
            if (!isBlank(refundOrder.getRefundReason())) {
                bizContent.put("refund_reason", refundOrder.getRefundReason());
            }
            AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
            refundRequest.setBizContent(bizContent.toJSONString());
            AlipayTradeRefundResponse response = buildClient(request).execute(refundRequest);
            if (!response.isSuccess()) {
                return failResult(response);
            }
            PayChannelResult result = PayChannelResult.success(response.getBody());
            result.setRefundStatus(PayRefundStatusEnum.SUCCESS);
            result.setChannelTradeNo(response.getTradeNo());
            result.setChannelRefundNo(refundOrder.getRefundNo());
            result.setSuccessTime(response.getGmtRefundPay());
            return result;
        } catch (Exception e) {
            return PayChannelResult.fail(e.getMessage());
        }
    }

    @Override
    public PayChannelResult query(PayChannelRequest request) {
        try {
            AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
            queryRequest.setBizContent(baseBizContent(request.getOrder(), null).toJSONString());
            AlipayTradeQueryResponse response = buildClient(request).execute(queryRequest);
            if (!response.isSuccess()) {
                return failResult(response);
            }
            PayChannelResult result = PayChannelResult.success(response.getBody());
            result.setChannelTradeNo(response.getTradeNo());
            result.setAmount(alipayYuanToCent(response.getTotalAmount()));
            result.setSuccessTime(response.getSendPayDate());
            result.setOrderStatus(mapTradeStatus(response.getTradeStatus()));
            return result;
        } catch (Exception e) {
            return PayChannelResult.fail(e.getMessage());
        }
    }

    @Override
    public PayNotifyMessage parsePayNotify(PayChannelRequest request) {
        PayNotifyMessage message = buildMessageFromParams(request, "out_trade_no", "total_amount");
        String tradeStatus = request.getParams().get("trade_status");
        message.setSuccess("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus));
        message.setVerifySuccess(verifyNotify(request));
        message.setSuccessTime(parseAlipayTime(firstNonBlank(request.getParams().get("gmt_payment"), request.getParams().get("notify_time"))));
        return message;
    }

    @Override
    public PayNotifyMessage parseRefundNotify(PayChannelRequest request) {
        PayNotifyMessage message = buildMessageFromParams(request, "out_trade_no", "refund_fee");
        String tradeStatus = request.getParams().get("trade_status");
        message.setSuccess("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus));
        message.setVerifySuccess(verifyNotify(request));
        message.setSuccessTime(parseAlipayTime(firstNonBlank(request.getParams().get("gmt_refund"), request.getParams().get("notify_time"))));
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

    private PayCreateResult createNativePay(PayChannelRequest request) {
        try {
            JSONObject bizContent = baseBizContent(request.getOrder(), "FACE_TO_FACE_PAYMENT");
            AlipayTradePrecreateRequest payRequest = new AlipayTradePrecreateRequest();
            payRequest.setNotifyUrl(resolveNotifyUrl(request));
            payRequest.setBizContent(bizContent.toJSONString());
            AlipayTradePrecreateResponse response = buildClient(request).execute(payRequest);
            assertSuccess(response, "支付宝扫码下单失败");

            PayCreateResult result = baseCreateResult(request);
            result.setCodeUrl(response.getQrCode());
            result.getPayParams().put("qrCode", response.getQrCode());
            result.getPayParams().put("outTradeNo", response.getOutTradeNo());
            result.getPayParams().put("rawResponse", response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new PayException("支付宝扫码下单失败：" + e.getMessage());
        }
    }

    private PayCreateResult createPagePay(PayChannelRequest request) {
        try {
            JSONObject bizContent = baseBizContent(request.getOrder(), "FAST_INSTANT_TRADE_PAY");
            AlipayTradePagePayRequest payRequest = new AlipayTradePagePayRequest();
            payRequest.setNotifyUrl(resolveNotifyUrl(request));
            payRequest.setReturnUrl(resolveReturnUrl(request));
            payRequest.setBizContent(bizContent.toJSONString());
            AlipayTradePagePayResponse response = buildClient(request).pageExecute(payRequest, "POST");
            assertPageResponse(response, "支付宝网页下单失败");

            PayCreateResult result = baseCreateResult(request);
            result.setPayForm(response.getBody());
            result.getPayParams().put("payForm", response.getBody());
            result.getPayParams().put("rawResponse", response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new PayException("支付宝网页下单失败：" + e.getMessage());
        }
    }

    private PayCreateResult createWapPay(PayChannelRequest request) {
        try {
            JSONObject bizContent = baseBizContent(request.getOrder(), "QUICK_WAP_WAY");
            AlipayTradeWapPayRequest payRequest = new AlipayTradeWapPayRequest();
            payRequest.setNotifyUrl(resolveNotifyUrl(request));
            payRequest.setReturnUrl(resolveReturnUrl(request));
            payRequest.setBizContent(bizContent.toJSONString());
            AlipayTradeWapPayResponse response = buildClient(request).pageExecute(payRequest, "POST");
            assertPageResponse(response, "支付宝 H5 下单失败");

            PayCreateResult result = baseCreateResult(request);
            result.setPayForm(response.getBody());
            result.getPayParams().put("payForm", response.getBody());
            result.getPayParams().put("rawResponse", response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new PayException("支付宝 H5 下单失败：" + e.getMessage());
        }
    }

    private PayCreateResult createAppPay(PayChannelRequest request) {
        try {
            JSONObject bizContent = baseBizContent(request.getOrder(), "QUICK_MSECURITY_PAY");
            AlipayTradeAppPayRequest payRequest = new AlipayTradeAppPayRequest();
            payRequest.setNotifyUrl(resolveNotifyUrl(request));
            payRequest.setBizContent(bizContent.toJSONString());
            AlipayTradeAppPayResponse response = buildClient(request).sdkExecute(payRequest);
            assertPageResponse(response, "支付宝 APP 下单失败");

            PayCreateResult result = baseCreateResult(request);
            result.getPayParams().put("orderString", response.getBody());
            result.getPayParams().put("rawResponse", response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new PayException("支付宝 APP 下单失败：" + e.getMessage());
        }
    }

    private PayCreateResult baseCreateResult(PayChannelRequest request) {
        PayCreateResult result = new PayCreateResult();
        result.setPayOrderNo(request.getOrder().getPayOrderNo());
        result.setChannelCode(getChannelCode());
        result.setPayScene(PaySceneEnum.valueOf(request.getOrder().getPayScene()));
        result.getPayParams().put("payOrderNo", request.getOrder().getPayOrderNo());
        result.getPayParams().put("channelCode", getChannelCode().name());
        result.getPayParams().put("payScene", request.getOrder().getPayScene());
        return result;
    }

    private JSONObject baseBizContent(PayOrderEntity order, String productCode) {
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", order.getPayOrderNo());
        bizContent.put("total_amount", centToYuan(order.getTotalAmount()));
        bizContent.put("subject", trimLength(firstNonBlank(order.getSubject(), "Payment order"), 256));
        if (!isBlank(order.getBody())) {
            bizContent.put("body", trimLength(order.getBody(), 128));
        }
        if (!isBlank(productCode)) {
            bizContent.put("product_code", productCode);
        }
        if (order.getExpireTime() != null) {
            bizContent.put("time_expire", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(order.getExpireTime()));
        }
        return bizContent;
    }

    private AlipayClient buildClient(PayChannelRequest request) {
        PayChannelConfigEntity config = request.getChannelConfig();
        JSONObject configJson = parseConfigJson(config);
        String gatewayUrl = configJson.getString("gatewayUrl");
        String appId = firstNonBlank(config.getAppId(), configJson.getString("appId"));
        String appPrivateKey = configJson.getString("appPrivateKey");
        String alipayPublicKey = configJson.getString("alipayPublicKey");
        String format = configJson.getString("format");
        String charset = configJson.getString("charset");
        String signType = configJson.getString("signType");
        requireNotBlank(gatewayUrl, "支付宝网关地址不能为空");
        requireNotBlank(appId, "支付宝 AppId 不能为空");
        requireNotBlank(appPrivateKey, "支付宝应用私钥不能为空");
        requireNotBlank(alipayPublicKey, "支付宝公钥不能为空");
        requireNotBlank(format, "支付宝接口格式不能为空");
        requireNotBlank(charset, "支付宝字符集不能为空");
        requireNotBlank(signType, "支付宝签名方式不能为空");
        return new DefaultAlipayClient(gatewayUrl, appId, appPrivateKey, format, charset, alipayPublicKey, signType);
    }

    private boolean verifyNotify(PayChannelRequest request) {
        try {
            PayChannelConfigEntity config = request.getChannelConfig();
            JSONObject configJson = parseConfigJson(config);
            String alipayPublicKey = configJson.getString("alipayPublicKey");
            String charset = configJson.getString("charset");
            String signType = configJson.getString("signType");
            if (isBlank(alipayPublicKey)) {
                return false;
            }
            if (!AlipaySignature.rsaCheckV1(request.getParams(), alipayPublicKey, charset, signType)) {
                return false;
            }
            String configuredAppId = firstNonBlank(config.getAppId(), configJson.getString("appId"));
            String callbackAppId = request.getParams().get("app_id");
            if (!isBlank(configuredAppId) && !configuredAppId.equals(callbackAppId)) {
                return false;
            }
            String configuredMerchantId = firstNonBlank(config.getMerchantId(), configJson.getString("merchantId"));
            String callbackSellerId = request.getParams().get("seller_id");
            return isBlank(configuredMerchantId) || configuredMerchantId.equals(callbackSellerId);
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject parseConfigJson(PayChannelConfigEntity config) {
        if (config == null || isBlank(config.getConfigJson())) {
            return new JSONObject();
        }
        JSONObject json = JSON.parseObject(config.getConfigJson());
        return json == null ? new JSONObject() : json;
    }

    private String resolveNotifyUrl(PayChannelRequest request) {
        JSONObject configJson = parseConfigJson(request.getChannelConfig());
        String notifyUrl = firstNonBlank(request.getChannelConfig().getNotifyUrl(), configJson.getString("notifyUrl"));
        requireNotBlank(notifyUrl, "支付宝支付回调地址不能为空");
        return notifyUrl;
    }

    private String resolveReturnUrl(PayChannelRequest request) {
        JSONObject configJson = parseConfigJson(request.getChannelConfig());
        return firstNonBlank(request.getReturnUrl(), configJson.getString("returnUrl"), "");
    }

    private PayChannelResult failResult(AlipayResponse response) {
        PayChannelResult result = PayChannelResult.fail(resolveErrorMessage(response));
        result.setErrorCode(firstNonBlank(response.getSubCode(), response.getCode()));
        result.setRawResponse(response.getBody());
        return result;
    }

    private void assertSuccess(AlipayResponse response, String prefix) {
        if (response == null || !response.isSuccess()) {
            throw new PayException(prefix + "：" + resolveErrorMessage(response));
        }
    }

    private void assertPageResponse(AlipayResponse response, String prefix) {
        if (response == null) {
            throw new PayException(prefix + "：支付宝无响应");
        }
        if (!isBlank(response.getSubCode())) {
            throw new PayException(prefix + "：" + resolveErrorMessage(response));
        }
        if (isBlank(response.getBody())) {
            throw new PayException(prefix + "：支付宝未返回支付参数");
        }
    }

    private String resolveErrorMessage(AlipayResponse response) {
        if (response == null) {
            return "支付宝无响应";
        }
        return firstNonBlank(response.getSubMsg(), response.getMsg(), response.getBody(), "未知错误");
    }

    private PayOrderStatusEnum mapTradeStatus(String tradeStatus) {
        if ("WAIT_BUYER_PAY".equals(tradeStatus)) {
            return PayOrderStatusEnum.PAYING;
        }
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            return PayOrderStatusEnum.PAID;
        }
        if ("TRADE_CLOSED".equals(tradeStatus)) {
            return PayOrderStatusEnum.CLOSED;
        }
        return PayOrderStatusEnum.UNKNOWN;
    }

    private String centToYuan(Long amount) {
        if (amount == null) {
            return "0.00";
        }
        return new BigDecimal(amount).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private Long alipayYuanToCent(String amount) {
        if (isBlank(amount)) {
            return null;
        }
        return new BigDecimal(amount.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    private Date parseAlipayTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private String trimLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throw new PayException(message);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

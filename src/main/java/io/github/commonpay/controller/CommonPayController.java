package io.github.commonpay.controller;

import io.github.commonpay.api.PayApiResponse;
import io.github.commonpay.entity.PayOrderEntity;
import io.github.commonpay.entity.PayRefundOrderEntity;
import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.model.PayCloseRequest;
import io.github.commonpay.model.PayCreateRequest;
import io.github.commonpay.model.PayCreateResult;
import io.github.commonpay.model.PayQueryRequest;
import io.github.commonpay.model.PayRefundQueryRequest;
import io.github.commonpay.model.PayRefundRequest;
import io.github.commonpay.service.CommonPayService;
import io.github.commonpay.tenant.PayTenantDataSourceSwitcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Optional HTTP facade for the payment module.
 *
 * <p>Applications that expose their own API can inject {@link CommonPayService}
 * directly and do not need to register this controller.</p>
 */
@Slf4j
@RestController
@RequestMapping("/pay")
@ConditionalOnProperty(prefix = "common.pay.web", name = "enabled", havingValue = "true")
public class CommonPayController {

    private final CommonPayService commonPayService;
    private final PayTenantDataSourceSwitcher tenantDataSourceSwitcher;

    public CommonPayController(CommonPayService commonPayService,
                               PayTenantDataSourceSwitcher tenantDataSourceSwitcher) {
        this.commonPayService = commonPayService;
        this.tenantDataSourceSwitcher = tenantDataSourceSwitcher;
    }

    @PostMapping("/order/create")
    public PayApiResponse<PayCreateResult> createPayOrder(@RequestBody PayCreateRequest request) {
        return PayApiResponse.success(commonPayService.createPayOrder(request));
    }

    @PostMapping("/order/query")
    public PayApiResponse<PayOrderEntity> queryPayOrder(@RequestBody PayQueryRequest request) {
        return PayApiResponse.success(commonPayService.queryPayOrder(request));
    }

    @PostMapping("/order/close")
    public PayApiResponse<PayOrderEntity> closePayOrder(@RequestBody PayCloseRequest request) {
        return PayApiResponse.success(commonPayService.closePayOrder(request));
    }

    @PostMapping("/refund/create")
    public PayApiResponse<PayRefundOrderEntity> createRefund(@RequestBody PayRefundRequest request) {
        return PayApiResponse.success(commonPayService.createRefund(request));
    }

    @PostMapping("/refund/query")
    public PayApiResponse<PayRefundOrderEntity> queryRefund(@RequestBody PayRefundQueryRequest request) {
        return PayApiResponse.success(commonPayService.queryRefund(request));
    }

    @PostMapping("/notify/{tenantId}/{channelCode}/{payAppCode}")
    public String payNotify(@PathVariable("tenantId") String tenantId,
                            @PathVariable("channelCode") String channelCode,
                            @PathVariable("payAppCode") String payAppCode,
                            @RequestParam Map<String, String> params,
                            @RequestHeader Map<String, String> headers,
                            @RequestBody(required = false) String rawBody) {
        PayChannelCodeEnum channel = parseChannel(channelCode);
        if (channel == null) {
            return "fail";
        }
        try {
            tenantDataSourceSwitcher.use(tenantId);
            return commonPayService.handlePayNotify(tenantId, channel, payAppCode, params, headers, rawBody);
        } catch (Exception e) {
            log.error("Payment callback handling failed, tenantId={}", tenantId, e);
            return failResponse(channel);
        } finally {
            tenantDataSourceSwitcher.clear();
        }
    }

    @PostMapping("/refund/notify/{tenantId}/{channelCode}/{payAppCode}")
    public String refundNotify(@PathVariable("tenantId") String tenantId,
                               @PathVariable("channelCode") String channelCode,
                               @PathVariable("payAppCode") String payAppCode,
                               @RequestParam Map<String, String> params,
                               @RequestHeader Map<String, String> headers,
                               @RequestBody(required = false) String rawBody) {
        PayChannelCodeEnum channel = parseChannel(channelCode);
        if (channel == null) {
            return "fail";
        }
        try {
            tenantDataSourceSwitcher.use(tenantId);
            return commonPayService.handleRefundNotify(tenantId, channel, payAppCode, params, headers, rawBody);
        } catch (Exception e) {
            log.error("Refund callback handling failed, tenantId={}", tenantId, e);
            return failResponse(channel);
        } finally {
            tenantDataSourceSwitcher.clear();
        }
    }

    private PayChannelCodeEnum parseChannel(String channelCode) {
        try {
            return PayChannelCodeEnum.valueOf(channelCode);
        } catch (Exception e) {
            return null;
        }
    }

    private String failResponse(PayChannelCodeEnum channelCode) {
        if (PayChannelCodeEnum.WECHAT.equals(channelCode)) {
            return "{\"code\":\"FAIL\",\"message\":\"processing failed\"}";
        }
        return "fail";
    }
}

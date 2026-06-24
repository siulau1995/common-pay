package io.github.commonpay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.commonpay.channel.PayChannelAdapter;
import io.github.commonpay.channel.PayChannelRegistry;
import io.github.commonpay.entity.PayChannelConfigEntity;
import io.github.commonpay.entity.PayNotifyRecordEntity;
import io.github.commonpay.entity.PayOrderEntity;
import io.github.commonpay.entity.PayOrderItemEntity;
import io.github.commonpay.entity.PayRefundItemEntity;
import io.github.commonpay.entity.PayRefundOrderEntity;
import io.github.commonpay.entity.PayStatusRecordEntity;
import io.github.commonpay.entity.PayTransactionEntity;
import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.enums.PayHandleStatusEnum;
import io.github.commonpay.enums.PayNotifyTypeEnum;
import io.github.commonpay.enums.PayObjectTypeEnum;
import io.github.commonpay.enums.PayOrderStatusEnum;
import io.github.commonpay.enums.PayRefundStatusEnum;
import io.github.commonpay.enums.PayTradeTypeEnum;
import io.github.commonpay.enums.PayVerifyStatusEnum;
import io.github.commonpay.mapper.PayChannelConfigMapper;
import io.github.commonpay.mapper.PayNotifyRecordMapper;
import io.github.commonpay.mapper.PayOrderItemMapper;
import io.github.commonpay.mapper.PayOrderMapper;
import io.github.commonpay.mapper.PayRefundItemMapper;
import io.github.commonpay.mapper.PayRefundOrderMapper;
import io.github.commonpay.mapper.PayStatusRecordMapper;
import io.github.commonpay.mapper.PayTransactionMapper;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayChannelResult;
import io.github.commonpay.model.PayCloseRequest;
import io.github.commonpay.model.PayClosedContext;
import io.github.commonpay.model.PayCreateItemRequest;
import io.github.commonpay.model.PayCreateRequest;
import io.github.commonpay.model.PayCreateResult;
import io.github.commonpay.model.PayExpiredContext;
import io.github.commonpay.model.PayNotifyMessage;
import io.github.commonpay.model.PayQueryRequest;
import io.github.commonpay.model.PayRefundItemRequest;
import io.github.commonpay.model.PayRefundQueryRequest;
import io.github.commonpay.model.PayRefundRequest;
import io.github.commonpay.model.PaySuccessContext;
import io.github.commonpay.model.RefundSuccessContext;
import io.github.commonpay.service.CommonPayService;
import io.github.commonpay.service.PayBusinessHandler;
import io.github.commonpay.util.PayException;
import io.github.commonpay.util.PayRedisLock;
import io.github.commonpay.util.PayIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CommonPayServiceImpl implements CommonPayService {

    private static final String DEFAULT_CURRENCY = "CNY";
    private static final int DEFAULT_EXPIRE_MINUTES = 30;
    private static final long NOTIFY_LOCK_SECONDS = 30L;
    private static final long STATE_CHANGE_LOCK_SECONDS = 60L;
    private static final String DEFAULT_PAY_APP_CODE = "DEFAULT_PAY";

    @Autowired
    private PayOrderMapper payOrderMapper;
    @Autowired
    private PayChannelConfigMapper payChannelConfigMapper;
    @Autowired
    private PayOrderItemMapper payOrderItemMapper;
    @Autowired
    private PayTransactionMapper payTransactionMapper;
    @Autowired
    private PayRefundOrderMapper payRefundOrderMapper;
    @Autowired
    private PayRefundItemMapper payRefundItemMapper;
    @Autowired
    private PayNotifyRecordMapper payNotifyRecordMapper;
    @Autowired
    private PayStatusRecordMapper payStatusRecordMapper;
    @Autowired
    private PayChannelRegistry payChannelRegistry;
    @Autowired
    private PayRedisLock payRedisLock;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired(required = false)
    private List<PayBusinessHandler> payBusinessHandlers = new ArrayList<>();

    @Override
    public PayCreateResult createPayOrder(PayCreateRequest request) {
        validateCreateRequest(request);
        request.setPayAppCode(normalizePayAppCode(request.getPayAppCode()));
        String lockName = businessLockName(request.getBizType(), request.getRefTable(), request.getRefValue());
        String lockValue = requireLock(lockName, STATE_CHANGE_LOCK_SECONDS);
        try {
            return transactionTemplate.execute(status -> createPayOrderInTransaction(request));
        } finally {
            payRedisLock.unlock(lockName, lockValue);
        }
    }

    private PayCreateResult createPayOrderInTransaction(PayCreateRequest request) {
        expireOldActiveOrder(request);

        PayOrderEntity order = buildPayOrder(request);
        payOrderMapper.insert(order);

        List<PayCreateItemRequest> items = normalizeItems(request);
        for (PayCreateItemRequest itemRequest : items) {
            PayOrderItemEntity item = buildPayOrderItem(order, itemRequest);
            payOrderItemMapper.insert(item);
        }

        PayChannelAdapter adapter = payChannelRegistry.get(request.getChannelCode());
        PayChannelConfigEntity channelConfig = getEnabledChannelConfig(request.getPayAppCode(), request.getChannelCode());
        PayChannelRequest channelRequest = buildChannelRequest(request.getTenantId(), request.getPayAppCode(), channelConfig, order, items, request.getReturnUrl());
        PayCreateResult result = adapter.prepay(channelRequest);

        saveTransaction(order, PayTradeTypeEnum.PAY.name(), PayHandleStatusEnum.SUCCESS.name(), request.getTotalAmount(), JSON.toJSONString(request), JSON.toJSONString(result), null, null);
        return result;
    }

    @Override
    public PayOrderEntity queryPayOrder(PayQueryRequest request) {
        if (request == null || isBlank(request.getPayOrderNo())) {
            throw new PayException("支付订单号不能为空");
        }
        PayOrderEntity order = getOrderByNo(request.getPayOrderNo());
        checkAndExpire(order);
        return order;
    }

    @Override
    public PayOrderEntity closePayOrder(PayCloseRequest request) {
        if (request == null || isBlank(request.getPayOrderNo()) || request.getChannelCode() == null) {
            throw new PayException("关单参数不完整");
        }
        String lockName = orderLockName(request.getPayOrderNo());
        String lockValue = requireLock(lockName, STATE_CHANGE_LOCK_SECONDS);
        try {
            return transactionTemplate.execute(status -> closePayOrderInTransaction(request));
        } finally {
            payRedisLock.unlock(lockName, lockValue);
        }
    }

    private PayOrderEntity closePayOrderInTransaction(PayCloseRequest request) {
        PayOrderEntity order = getOrderByNo(request.getPayOrderNo());
        if (PayOrderStatusEnum.PAID.name().equals(order.getOrderStatus())) {
            throw new PayException("已支付订单不能关闭");
        }
        PayChannelAdapter adapter = payChannelRegistry.get(request.getChannelCode());
        String payAppCode = normalizePayAppCode(request.getPayAppCode());
        PayChannelConfigEntity channelConfig = getEnabledChannelConfig(payAppCode, request.getChannelCode());
        PayChannelResult result = adapter.close(buildChannelRequest(null, payAppCode, channelConfig, order, null, null));
        if (result == null || !result.isSuccess()) {
            throw new PayException("支付渠道关单失败：" + (result == null ? "empty response" : result.getErrorMessage()));
        }
        String beforeStatus = order.getOrderStatus();
        order.setOrderStatus(PayOrderStatusEnum.CLOSED.name());
        order.setCloseTime(new Date());
        touch(order);
        payOrderMapper.updateById(order);
        updateItemsStatus(order.getId(), PayOrderStatusEnum.CLOSED.name(), 0L);
        saveStatus(order, PayObjectTypeEnum.ORDER.name(), beforeStatus, PayOrderStatusEnum.CLOSED.name(), PayTradeTypeEnum.CLOSE.name(), request.getCloseReason());
        saveTransaction(order, PayTradeTypeEnum.CLOSE.name(), result.isSuccess() ? PayHandleStatusEnum.SUCCESS.name() : PayHandleStatusEnum.FAILED.name(), null, JSON.toJSONString(request), result.getRawResponse(), result.getErrorCode(), result.getErrorMessage());
        notifyPayClosed(order, request.getCloseReason());
        return order;
    }

    @Override
    public PayRefundOrderEntity createRefund(PayRefundRequest request) {
        validateRefundRequest(request);
        String lockName = orderLockName(request.getPayOrderNo());
        String lockValue = requireLock(lockName, STATE_CHANGE_LOCK_SECONDS);
        try {
            return transactionTemplate.execute(status -> createRefundInTransaction(request));
        } finally {
            payRedisLock.unlock(lockName, lockValue);
        }
    }

    private PayRefundOrderEntity createRefundInTransaction(PayRefundRequest request) {
        if (!isBlank(request.getRefundNo())) {
            PayRefundOrderEntity existing = findRefundByNo(request.getRefundNo());
            if (existing != null) {
                if (!request.getPayOrderNo().equals(existing.getPayOrderNo())) {
                    throw new PayException("退款幂等号已被其他支付订单使用");
                }
                return existing;
            }
        }
        PayOrderEntity order = getOrderByNo(request.getPayOrderNo());
        if (!PayOrderStatusEnum.PAID.name().equals(order.getOrderStatus()) && !PayOrderStatusEnum.PARTIAL_REFUND.name().equals(order.getOrderStatus())) {
            throw new PayException("当前订单状态不允许退款");
        }
        long refundableAmount = safeAmount(order.getPaidAmount()) - safeAmount(order.getRefundAmount());
        if (request.getRefundAmount() > refundableAmount) {
            throw new PayException("退款金额超过可退金额");
        }

        PayRefundOrderEntity refundOrder = buildRefundOrder(order, request);
        payRefundOrderMapper.insert(refundOrder);
        if (request.getItems() != null) {
            for (PayRefundItemRequest itemRequest : request.getItems()) {
                payRefundItemMapper.insert(buildRefundItem(refundOrder, itemRequest));
            }
        }

        order.setOrderStatus(PayOrderStatusEnum.REFUNDING.name());
        touch(order);
        payOrderMapper.updateById(order);

        PayChannelAdapter adapter = payChannelRegistry.get(request.getChannelCode());
        String payAppCode = normalizePayAppCode(request.getPayAppCode());
        PayChannelConfigEntity channelConfig = getEnabledChannelConfig(payAppCode, request.getChannelCode());
        PayChannelRequest channelRequest = buildChannelRequest(null, payAppCode, channelConfig, order, null, null);
        channelRequest.setRefundOrder(refundOrder);
        PayChannelResult result = adapter.refund(channelRequest);
        if (result == null || !result.isSuccess()) {
            throw new PayException("支付渠道退款申请失败：" + (result == null ? "empty response" : result.getErrorMessage()));
        }
        saveTransaction(order, PayTradeTypeEnum.REFUND.name(), result.isSuccess() ? PayHandleStatusEnum.SUCCESS.name() : PayHandleStatusEnum.FAILED.name(), request.getRefundAmount(), JSON.toJSONString(request), result.getRawResponse(), result.getErrorCode(), result.getErrorMessage());
        return refundOrder;
    }

    @Override
    public PayRefundOrderEntity queryRefund(PayRefundQueryRequest request) {
        if (request == null || isBlank(request.getRefundNo())) {
            throw new PayException("退款单号不能为空");
        }
        return getRefundByNo(request.getRefundNo());
    }

    @Override
    public String handlePayNotify(String tenantId, PayChannelCodeEnum channelCode, String payAppCode, Map<String, String> params, Map<String, String> headers, String rawBody) {
        PayChannelAdapter adapter = payChannelRegistry.get(channelCode);
        PayChannelRequest request;
        try {
            PayChannelConfigEntity channelConfig = getEnabledChannelConfig(payAppCode, channelCode);
            request = buildNotifyRequest(tenantId, normalizePayAppCode(payAppCode), channelConfig, params, headers, rawBody);
        } catch (Exception e) {
            log.error("支付回调渠道配置读取失败，tenantId={}, channelCode={}, payAppCode={}", tenantId, channelCode, payAppCode, e);
            return adapter.buildPayNotifyFailResponse("notify config error");
        }
        PayNotifyMessage message;
        try {
            message = adapter.parsePayNotify(request);
        } catch (Exception e) {
            log.error("支付回调解析失败", e);
            return adapter.buildPayNotifyFailResponse("notify parse error");
        }
        if (isBlank(message.getPayOrderNo())) {
            return adapter.buildPayNotifyFailResponse("pay order no empty");
        }
        String lockName = orderLockName(message.getPayOrderNo());
        String lockValue = payRedisLock.lock(lockName, NOTIFY_LOCK_SECONDS);
        if (lockValue == null) {
            return adapter.buildPayNotifyFailResponse("notify processing");
        }
        try {
            return transactionTemplate.execute(status -> handlePayNotifyLocked(adapter, channelCode, message));
        } finally {
            payRedisLock.unlock(lockName, lockValue);
        }
    }

    @Override
    public String handleRefundNotify(String tenantId, PayChannelCodeEnum channelCode, String payAppCode, Map<String, String> params, Map<String, String> headers, String rawBody) {
        PayChannelAdapter adapter = payChannelRegistry.get(channelCode);
        PayChannelRequest request;
        try {
            PayChannelConfigEntity channelConfig = getEnabledChannelConfig(payAppCode, channelCode);
            request = buildNotifyRequest(tenantId, normalizePayAppCode(payAppCode), channelConfig, params, headers, rawBody);
        } catch (Exception e) {
            log.error("退款回调渠道配置读取失败，tenantId={}, channelCode={}, payAppCode={}", tenantId, channelCode, payAppCode, e);
            return adapter.buildRefundNotifyFailResponse("refund notify config error");
        }
        PayNotifyMessage message;
        try {
            message = adapter.parseRefundNotify(request);
        } catch (Exception e) {
            log.error("退款回调解析失败", e);
            return adapter.buildRefundNotifyFailResponse("refund notify parse error");
        }
        if (isBlank(message.getRefundNo())) {
            return adapter.buildRefundNotifyFailResponse("refund no empty");
        }
        String lockName = refundLockName(message.getRefundNo());
        String lockValue = payRedisLock.lock(lockName, NOTIFY_LOCK_SECONDS);
        if (lockValue == null) {
            return adapter.buildRefundNotifyFailResponse("refund notify processing");
        }
        try {
            return transactionTemplate.execute(status -> handleRefundNotifyLocked(adapter, channelCode, message));
        } finally {
            payRedisLock.unlock(lockName, lockValue);
        }
    }

    public String handlePayNotifyLocked(PayChannelAdapter adapter, PayChannelCodeEnum channelCode, PayNotifyMessage message) {
        PayNotifyRecordEntity record = findNotifyRecord(channelCode, PayNotifyTypeEnum.PAY.name(), message.getChannelNotifyId());
        if (record != null && PayHandleStatusEnum.SUCCESS.name().equals(record.getNotifyStatus())) {
            // A provider may retry the same signed notification after receiving a slow response.
            // The state transition already completed, so acknowledge it without inserting a duplicate row.
            return message.isVerifySuccess()
                    ? adapter.buildPayNotifySuccessResponse()
                    : adapter.buildPayNotifyFailResponse("验签失败");
        }
        if (record == null) {
            record = buildNotifyRecord(channelCode, PayNotifyTypeEnum.PAY.name(), message);
            payNotifyRecordMapper.insert(record);
        } else {
            refreshNotifyRecord(record, message);
        }
        try {
            PayOrderEntity order = getOrderByNo(message.getPayOrderNo());
            if (!channelCode.name().equals(order.getChannelCode())) {
                return failPayNotify(adapter, record, "支付渠道与订单不匹配");
            }
            if (!message.isVerifySuccess()) {
                return failPayNotify(adapter, record, "验签失败");
            }
            if (!amountEquals(order.getTotalAmount(), message.getAmount())) {
                return failPayNotify(adapter, record, "支付金额不匹配");
            }
            if (PayOrderStatusEnum.PAID.name().equals(order.getOrderStatus())) {
                return successPayNotify(adapter, record, null);
            }
            if (!PayOrderStatusEnum.WAIT_PAY.name().equals(order.getOrderStatus())
                    && !PayOrderStatusEnum.PAYING.name().equals(order.getOrderStatus())) {
                return successPayNotify(adapter, record,
                        "支付单当前状态为" + order.getOrderStatus() + "，忽略晚到支付回调");
            }
            if (!message.isSuccess()) {
                updateOrderFailed(order, message);
                return successPayNotify(adapter, record, null);
            }

            String beforeStatus = order.getOrderStatus();
            order.setOrderStatus(PayOrderStatusEnum.PAID.name());
            order.setPaidAmount(order.getTotalAmount());
            order.setPayTime(message.getSuccessTime() == null ? new Date() : message.getSuccessTime());
            order.setChannelTradeNo(message.getChannelTradeNo());
            touch(order);
            payOrderMapper.updateById(order);
            updateItemsStatus(order.getId(), PayOrderStatusEnum.PAID.name(), order.getTotalAmount());
            saveStatus(order, PayObjectTypeEnum.ORDER.name(), beforeStatus, PayOrderStatusEnum.PAID.name(), PayTradeTypeEnum.NOTIFY.name(), "支付回调成功");
            saveTransaction(order, PayTradeTypeEnum.NOTIFY.name(), PayHandleStatusEnum.SUCCESS.name(), message.getAmount(), message.getRawBody(), null, null, null);

            String businessError = notifyPaySuccess(order);
            return successPayNotify(adapter, record, businessError);
        } catch (Exception e) {
            log.error("支付回调处理失败", e);
            return failPayNotify(adapter, record, e.getMessage());
        }
    }

    public String handleRefundNotifyLocked(PayChannelAdapter adapter, PayChannelCodeEnum channelCode, PayNotifyMessage message) {
        PayNotifyRecordEntity record = findNotifyRecord(channelCode, PayNotifyTypeEnum.REFUND.name(), message.getChannelNotifyId());
        if (record != null && PayHandleStatusEnum.SUCCESS.name().equals(record.getNotifyStatus())) {
            // A successful refund notification is idempotent for the same provider notification id.
            return message.isVerifySuccess()
                    ? adapter.buildRefundNotifySuccessResponse()
                    : adapter.buildRefundNotifyFailResponse("验签失败");
        }
        if (record == null) {
            record = buildNotifyRecord(channelCode, PayNotifyTypeEnum.REFUND.name(), message);
            payNotifyRecordMapper.insert(record);
        } else {
            refreshNotifyRecord(record, message);
        }
        try {
            PayRefundOrderEntity refundOrder = getRefundByNo(message.getRefundNo());
            if (!channelCode.name().equals(refundOrder.getChannelCode())) {
                return failRefundNotify(adapter, record, "退款渠道与退款单不匹配");
            }
            if (!message.isVerifySuccess()) {
                return failRefundNotify(adapter, record, "验签失败");
            }
            if (!amountEquals(refundOrder.getRefundAmount(), message.getAmount())) {
                return failRefundNotify(adapter, record, "退款金额不匹配");
            }
            if (PayRefundStatusEnum.SUCCESS.name().equals(refundOrder.getRefundStatus())) {
                return successRefundNotify(adapter, record, null);
            }
            if (!message.isSuccess()) {
                refundOrder.setRefundStatus(PayRefundStatusEnum.FAILED.name());
                touch(refundOrder);
                payRefundOrderMapper.updateById(refundOrder);
                return successRefundNotify(adapter, record, null);
            }

            refundOrder.setRefundStatus(PayRefundStatusEnum.SUCCESS.name());
            refundOrder.setChannelRefundNo(message.getChannelRefundNo());
            refundOrder.setRefundTime(message.getSuccessTime() == null ? new Date() : message.getSuccessTime());
            touch(refundOrder);
            payRefundOrderMapper.updateById(refundOrder);

            PayOrderEntity order = getOrderByNo(refundOrder.getPayOrderNo());
            long newRefundAmount = safeAmount(order.getRefundAmount()) + safeAmount(refundOrder.getRefundAmount());
            order.setRefundAmount(newRefundAmount);
            order.setOrderStatus(newRefundAmount >= safeAmount(order.getPaidAmount()) ? PayOrderStatusEnum.REFUNDED.name() : PayOrderStatusEnum.PARTIAL_REFUND.name());
            touch(order);
            payOrderMapper.updateById(order);
            saveStatus(order, PayObjectTypeEnum.REFUND.name(), PayRefundStatusEnum.PROCESSING.name(), PayRefundStatusEnum.SUCCESS.name(), PayTradeTypeEnum.NOTIFY.name(), "退款回调成功");
            saveTransaction(order, PayTradeTypeEnum.NOTIFY.name(), PayHandleStatusEnum.SUCCESS.name(), message.getAmount(), message.getRawBody(), null, null, null);

            String businessError = notifyRefundSuccess(refundOrder);
            return successRefundNotify(adapter, record, businessError);
        } catch (Exception e) {
            log.error("退款回调处理失败", e);
            return failRefundNotify(adapter, record, e.getMessage());
        }
    }

    private void validateCreateRequest(PayCreateRequest request) {
        if (request == null) {
            throw new PayException("下单参数不能为空");
        }
        if (isBlank(request.getTenantId())) {
            throw new PayException("tenantId不能为空");
        }
        if (request.getChannelCode() == null) {
            throw new PayException("支付渠道不能为空");
        }
        if (request.getPayScene() == null) {
            throw new PayException("支付场景不能为空");
        }
        if (isBlank(request.getBizType()) || isBlank(request.getRefTable()) || isBlank(request.getRefValue())) {
            throw new PayException("业务绑定信息不能为空");
        }
        if (request.getTotalAmount() == null || request.getTotalAmount() <= 0) {
            throw new PayException("支付金额必须大于0");
        }
        long itemAmount = 0L;
        for (PayCreateItemRequest item : normalizeItems(request)) {
            if (item.getItemAmount() == null || item.getItemAmount() <= 0) {
                throw new PayException("子订单金额必须大于0");
            }
            itemAmount += item.getItemAmount();
        }
        if (itemAmount != request.getTotalAmount()) {
            throw new PayException("子订单金额合计必须等于支付总金额");
        }
        if (request.getExpireTime() != null && request.getExpireTime().before(new Date())) {
            throw new PayException("支付单过期时间不能早于当前时间");
        }
    }

    private void validateRefundRequest(PayRefundRequest request) {
        if (request == null || isBlank(request.getPayOrderNo()) || request.getChannelCode() == null) {
            throw new PayException("退款参数不完整");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount() <= 0) {
            throw new PayException("退款金额必须大于0");
        }
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            long itemAmount = 0L;
            for (PayRefundItemRequest item : request.getItems()) {
                if (item == null || item.getRefundAmount() == null || item.getRefundAmount() <= 0) {
                    throw new PayException("退款明细金额必须大于0");
                }
                itemAmount = Math.addExact(itemAmount, item.getRefundAmount());
            }
            if (itemAmount != request.getRefundAmount()) {
                throw new PayException("退款明细金额合计必须等于退款总金额");
            }
        }
    }

    private PayOrderEntity buildPayOrder(PayCreateRequest request) {
        Date now = new Date();
        PayOrderEntity order = new PayOrderEntity();
        fillCreate(order, now);
        order.setPayOrderNo(isBlank(request.getPayOrderNo()) ? generateNo("PAY") : request.getPayOrderNo().trim());
        order.setBizType(request.getBizType());
        order.setRefTable(request.getRefTable());
        order.setRefValue(request.getRefValue());
        order.setRefNo(request.getRefNo());
        order.setPayAppCode(request.getPayAppCode());
        order.setChannelCode(request.getChannelCode().name());
        order.setSubject(request.getSubject());
        order.setBody(request.getBody());
        order.setTotalAmount(request.getTotalAmount());
        order.setPaidAmount(0L);
        order.setRefundAmount(0L);
        order.setCurrency(DEFAULT_CURRENCY);
        order.setOrderStatus(PayOrderStatusEnum.WAIT_PAY.name());
        order.setPayScene(request.getPayScene().name());
        order.setClientIp(request.getClientIp());
        order.setOpenId(request.getOpenId());
        order.setExpireTime(request.getExpireTime() == null ? defaultExpireTime(now) : request.getExpireTime());
        order.setChannelOrderNo(order.getPayOrderNo());
        order.setExtraJson(request.getExtraJson());
        return order;
    }

    private PayOrderItemEntity buildPayOrderItem(PayOrderEntity order, PayCreateItemRequest itemRequest) {
        PayOrderItemEntity item = new PayOrderItemEntity();
        fillCreate(item, new Date());
        item.setPayOrderId(order.getId());
        item.setPayOrderNo(order.getPayOrderNo());
        item.setBizType(itemRequest.getBizType());
        item.setRefTable(itemRequest.getRefTable());
        item.setRefValue(itemRequest.getRefValue());
        item.setRefNo(itemRequest.getRefNo());
        item.setItemName(itemRequest.getItemName());
        item.setItemAmount(itemRequest.getItemAmount());
        item.setPaidAmount(0L);
        item.setRefundAmount(0L);
        item.setItemStatus(PayOrderStatusEnum.WAIT_PAY.name());
        return item;
    }

    private PayRefundOrderEntity buildRefundOrder(PayOrderEntity order, PayRefundRequest request) {
        PayRefundOrderEntity refundOrder = new PayRefundOrderEntity();
        fillCreate(refundOrder, new Date());
        refundOrder.setRefundNo(isBlank(request.getRefundNo()) ? generateNo("RFD") : request.getRefundNo().trim());
        refundOrder.setPayOrderId(order.getId());
        refundOrder.setPayOrderNo(order.getPayOrderNo());
        refundOrder.setBizType(request.getBizType());
        refundOrder.setRefTable(request.getRefTable());
        refundOrder.setRefValue(request.getRefValue());
        refundOrder.setRefNo(request.getRefNo());
        refundOrder.setChannelCode(request.getChannelCode().name());
        refundOrder.setRefundAmount(request.getRefundAmount());
        refundOrder.setRefundReason(request.getRefundReason());
        refundOrder.setRefundStatus(PayRefundStatusEnum.PROCESSING.name());
        refundOrder.setExtraJson(request.getExtraJson());
        return refundOrder;
    }

    private PayRefundItemEntity buildRefundItem(PayRefundOrderEntity refundOrder, PayRefundItemRequest request) {
        PayRefundItemEntity item = new PayRefundItemEntity();
        fillCreate(item, new Date());
        item.setRefundOrderId(refundOrder.getId());
        item.setRefundNo(refundOrder.getRefundNo());
        item.setPayOrderItemId(request.getPayOrderItemId());
        item.setRefTable(request.getRefTable());
        item.setRefValue(request.getRefValue());
        item.setRefNo(request.getRefNo());
        item.setRefundAmount(request.getRefundAmount());
        item.setRefundStatus(PayRefundStatusEnum.PROCESSING.name());
        return item;
    }

    private List<PayCreateItemRequest> normalizeItems(PayCreateRequest request) {
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            return request.getItems();
        }
        PayCreateItemRequest item = new PayCreateItemRequest();
        item.setBizType(request.getBizType());
        item.setRefTable(request.getRefTable());
        item.setRefValue(request.getRefValue());
        item.setRefNo(request.getRefNo());
        item.setItemName(request.getSubject());
        item.setItemAmount(request.getTotalAmount());
        List<PayCreateItemRequest> items = new ArrayList<>();
        items.add(item);
        return items;
    }

    private void expireOldActiveOrder(PayCreateRequest request) {
        List<PayOrderEntity> orders = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderEntity>()
                .eq(PayOrderEntity::getBizType, request.getBizType())
                .eq(PayOrderEntity::getRefTable, request.getRefTable())
                .eq(PayOrderEntity::getRefValue, request.getRefValue())
                .eq(PayOrderEntity::getDeleteMark, 0));
        for (PayOrderEntity order : orders) {
            checkAndExpire(order);
            if (PayOrderStatusEnum.PAID.name().equals(order.getOrderStatus())) {
                throw new PayException("业务对象已支付");
            }
            if (PayOrderStatusEnum.WAIT_PAY.name().equals(order.getOrderStatus()) || PayOrderStatusEnum.PAYING.name().equals(order.getOrderStatus())) {
                throw new PayException("业务对象已有未完成支付单");
            }
        }
    }

    private void checkAndExpire(PayOrderEntity order) {
        if (order == null || order.getExpireTime() == null) {
            return;
        }
        if (!PayOrderStatusEnum.WAIT_PAY.name().equals(order.getOrderStatus()) && !PayOrderStatusEnum.PAYING.name().equals(order.getOrderStatus())) {
            return;
        }
        if (order.getExpireTime().after(new Date())) {
            return;
        }
        String beforeStatus = order.getOrderStatus();
        order.setOrderStatus(PayOrderStatusEnum.EXPIRED.name());
        touch(order);
        payOrderMapper.updateById(order);
        updateItemsStatus(order.getId(), PayOrderStatusEnum.EXPIRED.name(), 0L);
        saveStatus(order, PayObjectTypeEnum.ORDER.name(), beforeStatus, PayOrderStatusEnum.EXPIRED.name(), "EXPIRE", "支付单已过期");
        notifyPayExpired(order);
    }

    private PayChannelRequest buildChannelRequest(String tenantId,
                                                  String payAppCode,
                                                  PayChannelConfigEntity channelConfig,
                                                  PayOrderEntity order,
                                                  List<PayCreateItemRequest> items,
                                                  String returnUrl) {
        PayChannelRequest request = new PayChannelRequest();
        request.setTenantId(tenantId);
        request.setPayAppCode(payAppCode);
        request.setChannelConfig(channelConfig);
        request.setOrder(order);
        request.setItems(items);
        request.setReturnUrl(returnUrl);
        return request;
    }

    private PayChannelRequest buildNotifyRequest(String tenantId, String payAppCode, PayChannelConfigEntity channelConfig, Map<String, String> params, Map<String, String> headers, String rawBody) {
        PayChannelRequest request = new PayChannelRequest();
        request.setTenantId(tenantId);
        request.setPayAppCode(payAppCode);
        request.setChannelConfig(channelConfig);
        request.setParams(params);
        request.setHeaders(headers);
        request.setRawBody(rawBody);
        return request;
    }

    private PayChannelConfigEntity getEnabledChannelConfig(String payAppCode, PayChannelCodeEnum channelCode) {
        if (channelCode == null) {
            throw new PayException("支付渠道不能为空");
        }
        String appCode = normalizePayAppCode(payAppCode);
        PayChannelConfigEntity config = payChannelConfigMapper.selectOne(new LambdaQueryWrapper<PayChannelConfigEntity>()
                .eq(PayChannelConfigEntity::getPayAppCode, appCode)
                .eq(PayChannelConfigEntity::getChannelCode, channelCode.name())
                .eq(PayChannelConfigEntity::getEnabledMark, 1)
                .eq(PayChannelConfigEntity::getDeleteMark, 0)
                .orderByAsc(PayChannelConfigEntity::getSortCode)
                .last("limit 1"));
        if (config == null) {
            throw new PayException("支付渠道配置不存在或未启用：" + channelCode.name() + "/" + appCode);
        }
        return config;
    }

    private String normalizePayAppCode(String payAppCode) {
        return isBlank(payAppCode) ? DEFAULT_PAY_APP_CODE : payAppCode.trim();
    }

    private PayOrderEntity getOrderByNo(String payOrderNo) {
        List<PayOrderEntity> orders = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderEntity>()
                .eq(PayOrderEntity::getPayOrderNo, payOrderNo)
                .eq(PayOrderEntity::getDeleteMark, 0));
        if (orders == null || orders.isEmpty()) {
            throw new PayException("支付订单不存在");
        }
        if (orders.size() > 1) {
            throw new PayException("支付订单号重复，请检查数据");
        }
        return orders.get(0);
    }

    private PayRefundOrderEntity getRefundByNo(String refundNo) {
        List<PayRefundOrderEntity> refunds = payRefundOrderMapper.selectList(new LambdaQueryWrapper<PayRefundOrderEntity>()
                .eq(PayRefundOrderEntity::getRefundNo, refundNo)
                .eq(PayRefundOrderEntity::getDeleteMark, 0));
        if (refunds == null || refunds.isEmpty()) {
            throw new PayException("退款单不存在");
        }
        if (refunds.size() > 1) {
            throw new PayException("退款单号重复，请检查数据");
        }
        return refunds.get(0);
    }

    private PayRefundOrderEntity findRefundByNo(String refundNo) {
        List<PayRefundOrderEntity> refunds = payRefundOrderMapper.selectList(new LambdaQueryWrapper<PayRefundOrderEntity>()
                .eq(PayRefundOrderEntity::getRefundNo, refundNo)
                .eq(PayRefundOrderEntity::getDeleteMark, 0));
        if (refunds == null || refunds.isEmpty()) {
            return null;
        }
        if (refunds.size() > 1) {
            throw new PayException("退款单号重复，请检查数据");
        }
        return refunds.get(0);
    }

    private void updateItemsStatus(String payOrderId, String status, Long paidAmount) {
        List<PayOrderItemEntity> items = payOrderItemMapper.selectList(new LambdaQueryWrapper<PayOrderItemEntity>()
                .eq(PayOrderItemEntity::getPayOrderId, payOrderId)
                .eq(PayOrderItemEntity::getDeleteMark, 0));
        for (PayOrderItemEntity item : items) {
            item.setItemStatus(status);
            if (PayOrderStatusEnum.PAID.name().equals(status)) {
                item.setPaidAmount(item.getItemAmount());
            } else if (paidAmount != null && paidAmount == 0L) {
                item.setPaidAmount(0L);
            }
            touch(item);
            payOrderItemMapper.updateById(item);
        }
    }

    private void updateOrderFailed(PayOrderEntity order, PayNotifyMessage message) {
        String beforeStatus = order.getOrderStatus();
        order.setOrderStatus(PayOrderStatusEnum.FAILED.name());
        order.setChannelTradeNo(message.getChannelTradeNo());
        touch(order);
        payOrderMapper.updateById(order);
        saveStatus(order, PayObjectTypeEnum.ORDER.name(), beforeStatus, PayOrderStatusEnum.FAILED.name(), PayTradeTypeEnum.NOTIFY.name(), "渠道通知支付失败");
    }

    private PayNotifyRecordEntity buildNotifyRecord(PayChannelCodeEnum channelCode, String notifyType, PayNotifyMessage message) {
        PayNotifyRecordEntity record = new PayNotifyRecordEntity();
        fillCreate(record, new Date());
        record.setNotifyNo(generateNo("NTF"));
        record.setNotifyType(notifyType);
        record.setChannelCode(channelCode.name());
        record.setPayOrderNo(message.getPayOrderNo());
        record.setRefundNo(message.getRefundNo());
        record.setChannelNotifyId(message.getChannelNotifyId());
        record.setChannelTradeNo(message.getChannelTradeNo());
        record.setNotifyStatus(PayHandleStatusEnum.RECEIVED.name());
        record.setVerifyStatus(message.isVerifySuccess() ? PayVerifyStatusEnum.SUCCESS.name() : PayVerifyStatusEnum.FAILED.name());
        record.setBusinessStatus(PayHandleStatusEnum.RECEIVED.name());
        record.setNotifyBody(message.getRawBody());
        record.setNotifyTime(message.getNotifyTime() == null ? new Date() : message.getNotifyTime());
        record.setRetryCount(0);
        return record;
    }

    private PayNotifyRecordEntity findNotifyRecord(PayChannelCodeEnum channelCode, String notifyType, String channelNotifyId) {
        if (isBlank(channelNotifyId)) {
            return null;
        }
        List<PayNotifyRecordEntity> records = payNotifyRecordMapper.selectList(new LambdaQueryWrapper<PayNotifyRecordEntity>()
                .eq(PayNotifyRecordEntity::getChannelCode, channelCode.name())
                .eq(PayNotifyRecordEntity::getChannelNotifyId, channelNotifyId)
                .eq(PayNotifyRecordEntity::getDeleteMark, 0));
        if (records == null || records.isEmpty()) {
            return null;
        }
        PayNotifyRecordEntity record = records.get(0);
        if (!notifyType.equals(record.getNotifyType())) {
            throw new PayException("渠道通知号已被其他通知类型使用");
        }
        return record;
    }

    private void refreshNotifyRecord(PayNotifyRecordEntity record, PayNotifyMessage message) {
        record.setPayOrderNo(message.getPayOrderNo());
        record.setRefundNo(message.getRefundNo());
        record.setChannelTradeNo(message.getChannelTradeNo());
        record.setNotifyStatus(PayHandleStatusEnum.RECEIVED.name());
        record.setVerifyStatus(message.isVerifySuccess() ? PayVerifyStatusEnum.SUCCESS.name() : PayVerifyStatusEnum.FAILED.name());
        record.setBusinessStatus(PayHandleStatusEnum.RECEIVED.name());
        record.setNotifyBody(message.getRawBody());
        record.setNotifyTime(message.getNotifyTime() == null ? new Date() : message.getNotifyTime());
        record.setErrorMessage(null);
        record.setResponseBody(null);
        record.setHandleTime(null);
        touch(record);
    }

    private String successPayNotify(PayChannelAdapter adapter, PayNotifyRecordEntity record, String businessError) {
        record.setNotifyStatus(PayHandleStatusEnum.SUCCESS.name());
        record.setBusinessStatus(isBlank(businessError) ? PayHandleStatusEnum.SUCCESS.name() : PayHandleStatusEnum.FAILED.name());
        record.setErrorMessage(businessError);
        record.setResponseBody(adapter.buildPayNotifySuccessResponse());
        record.setHandleTime(new Date());
        touch(record);
        payNotifyRecordMapper.updateById(record);
        return record.getResponseBody();
    }

    private String failPayNotify(PayChannelAdapter adapter, PayNotifyRecordEntity record, String message) {
        record.setNotifyStatus(PayHandleStatusEnum.FAILED.name());
        record.setBusinessStatus(PayHandleStatusEnum.FAILED.name());
        record.setErrorMessage(message);
        record.setResponseBody(adapter.buildPayNotifyFailResponse(message));
        record.setHandleTime(new Date());
        touch(record);
        payNotifyRecordMapper.updateById(record);
        return record.getResponseBody();
    }

    private String successRefundNotify(PayChannelAdapter adapter, PayNotifyRecordEntity record, String businessError) {
        record.setNotifyStatus(PayHandleStatusEnum.SUCCESS.name());
        record.setBusinessStatus(isBlank(businessError) ? PayHandleStatusEnum.SUCCESS.name() : PayHandleStatusEnum.FAILED.name());
        record.setErrorMessage(businessError);
        record.setResponseBody(adapter.buildRefundNotifySuccessResponse());
        record.setHandleTime(new Date());
        touch(record);
        payNotifyRecordMapper.updateById(record);
        return record.getResponseBody();
    }

    private String failRefundNotify(PayChannelAdapter adapter, PayNotifyRecordEntity record, String message) {
        record.setNotifyStatus(PayHandleStatusEnum.FAILED.name());
        record.setBusinessStatus(PayHandleStatusEnum.FAILED.name());
        record.setErrorMessage(message);
        record.setResponseBody(adapter.buildRefundNotifyFailResponse(message));
        record.setHandleTime(new Date());
        touch(record);
        payNotifyRecordMapper.updateById(record);
        return record.getResponseBody();
    }

    private void saveTransaction(PayOrderEntity order, String tradeType, String tradeStatus, Long amount, String requestBody, String responseBody, String errorCode, String errorMessage) {
        PayTransactionEntity transaction = new PayTransactionEntity();
        fillCreate(transaction, new Date());
        transaction.setPayOrderId(order.getId());
        transaction.setPayOrderNo(order.getPayOrderNo());
        transaction.setTransactionNo(generateNo("TRX"));
        transaction.setChannelCode(order.getChannelCode());
        transaction.setTradeType(tradeType);
        transaction.setTradeStatus(tradeStatus);
        transaction.setAmount(amount);
        transaction.setChannelTradeNo(order.getChannelTradeNo());
        transaction.setRequestBody(requestBody);
        transaction.setResponseBody(responseBody);
        transaction.setErrorCode(errorCode);
        transaction.setErrorMessage(errorMessage);
        transaction.setRequestTime(new Date());
        transaction.setResponseTime(new Date());
        payTransactionMapper.insert(transaction);
    }

    private void saveStatus(PayOrderEntity order, String objectType, String beforeStatus, String afterStatus, String changeType, String reason) {
        PayStatusRecordEntity record = new PayStatusRecordEntity();
        fillCreate(record, new Date());
        record.setObjectType(objectType);
        record.setObjectId(order.getId());
        record.setObjectNo(order.getPayOrderNo());
        record.setBeforeStatus(beforeStatus);
        record.setAfterStatus(afterStatus);
        record.setChangeType(changeType);
        record.setChangeReason(reason);
        record.setOperatorType("SYSTEM");
        record.setOperatorId("common-pay");
        payStatusRecordMapper.insert(record);
    }

    private String notifyPaySuccess(PayOrderEntity order) {
        String error = null;
        for (PayBusinessHandler handler : payBusinessHandlers) {
            if (handler.supports(order.getBizType())) {
                try {
                    handler.onPaySuccess(buildPaySuccessContext(order));
                } catch (Exception e) {
                    log.error("业务支付成功回调失败，payOrderNo={}", order.getPayOrderNo(), e);
                    error = e.getMessage();
                }
            }
        }
        return error;
    }

    private void notifyPayClosed(PayOrderEntity order, String reason) {
        for (PayBusinessHandler handler : payBusinessHandlers) {
            if (handler.supports(order.getBizType())) {
                PayClosedContext context = new PayClosedContext();
                context.setPayOrderNo(order.getPayOrderNo());
                context.setBizType(order.getBizType());
                context.setRefTable(order.getRefTable());
                context.setRefValue(order.getRefValue());
                context.setRefNo(order.getRefNo());
                context.setCloseReason(reason);
                handler.onPayClosed(context);
            }
        }
    }

    private void notifyPayExpired(PayOrderEntity order) {
        for (PayBusinessHandler handler : payBusinessHandlers) {
            if (handler.supports(order.getBizType())) {
                PayExpiredContext context = new PayExpiredContext();
                context.setPayOrderNo(order.getPayOrderNo());
                context.setBizType(order.getBizType());
                context.setRefTable(order.getRefTable());
                context.setRefValue(order.getRefValue());
                context.setRefNo(order.getRefNo());
                context.setExpireTime(order.getExpireTime());
                handler.onPayExpired(context);
            }
        }
    }

    private String notifyRefundSuccess(PayRefundOrderEntity refundOrder) {
        String error = null;
        for (PayBusinessHandler handler : payBusinessHandlers) {
            if (handler.supports(refundOrder.getBizType())) {
                try {
                    RefundSuccessContext context = new RefundSuccessContext();
                    context.setRefundNo(refundOrder.getRefundNo());
                    context.setPayOrderNo(refundOrder.getPayOrderNo());
                    context.setChannelCode(refundOrder.getChannelCode());
                    context.setChannelRefundNo(refundOrder.getChannelRefundNo());
                    context.setBizType(refundOrder.getBizType());
                    context.setRefTable(refundOrder.getRefTable());
                    context.setRefValue(refundOrder.getRefValue());
                    context.setRefNo(refundOrder.getRefNo());
                    context.setRefundAmount(refundOrder.getRefundAmount());
                    context.setRefundTime(refundOrder.getRefundTime());
                    handler.onRefundSuccess(context);
                } catch (Exception e) {
                    log.error("业务退款成功回调失败，refundNo={}", refundOrder.getRefundNo(), e);
                    error = e.getMessage();
                }
            }
        }
        return error;
    }

    private PaySuccessContext buildPaySuccessContext(PayOrderEntity order) {
        PaySuccessContext context = new PaySuccessContext();
        context.setPayOrderNo(order.getPayOrderNo());
        context.setChannelCode(order.getChannelCode());
        context.setChannelTradeNo(order.getChannelTradeNo());
        context.setBizType(order.getBizType());
        context.setRefTable(order.getRefTable());
        context.setRefValue(order.getRefValue());
        context.setRefNo(order.getRefNo());
        context.setPaidAmount(order.getPaidAmount());
        context.setPayTime(order.getPayTime());
        List<PayOrderItemEntity> itemEntities = payOrderItemMapper.selectList(new LambdaQueryWrapper<PayOrderItemEntity>()
                .eq(PayOrderItemEntity::getPayOrderId, order.getId())
                .eq(PayOrderItemEntity::getDeleteMark, 0));
        List<PayCreateItemRequest> items = new ArrayList<>();
        for (PayOrderItemEntity entity : itemEntities) {
            PayCreateItemRequest item = new PayCreateItemRequest();
            item.setBizType(entity.getBizType());
            item.setRefTable(entity.getRefTable());
            item.setRefValue(entity.getRefValue());
            item.setRefNo(entity.getRefNo());
            item.setItemName(entity.getItemName());
            item.setItemAmount(entity.getItemAmount());
            items.add(item);
        }
        context.setItems(items);
        return context;
    }

    private boolean amountEquals(Long expected, Long actual) {
        if (actual == null) {
            return false;
        }
        return safeAmount(expected) == safeAmount(actual);
    }

    private long safeAmount(Long amount) {
        return amount == null ? 0L : amount;
    }

    private void fillCreate(io.github.commonpay.entity.BasePayEntity entity, Date now) {
        entity.setId(PayIdGenerator.nextId());
        entity.setCreatorTime(now);
        entity.setLastModifyTime(now);
        entity.setDeleteMark(0);
    }

    private void touch(io.github.commonpay.entity.BasePayEntity entity) {
        entity.setLastModifyTime(new Date());
    }

    private Date defaultExpireTime(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MINUTE, DEFAULT_EXPIRE_MINUTES);
        return calendar.getTime();
    }

    private String generateNo(String prefix) {
        return prefix + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + PayIdGenerator.nextId();
    }

    private String requireLock(String lockName, long expireSeconds) {
        String lockValue = payRedisLock.lock(lockName, expireSeconds);
        if (lockValue == null) {
            throw new PayException("支付业务正在处理中，请稍后重试");
        }
        return lockValue;
    }

    private String businessLockName(String bizType, String refTable, String refValue) {
        return "business:" + bizType + ":" + refTable + ":" + refValue;
    }

    private String orderLockName(String payOrderNo) {
        return "order:" + payOrderNo;
    }

    private String refundLockName(String refundNo) {
        return "refund:" + refundNo;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

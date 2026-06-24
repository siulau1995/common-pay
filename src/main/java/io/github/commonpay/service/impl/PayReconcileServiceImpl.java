package io.github.commonpay.service.impl;

import com.alibaba.fastjson2.JSON;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.commonpay.channel.PayChannelAdapter;
import io.github.commonpay.channel.PayChannelRegistry;
import io.github.commonpay.entity.BasePayEntity;
import io.github.commonpay.entity.PayChannelConfigEntity;
import io.github.commonpay.entity.PayNotifyRecordEntity;
import io.github.commonpay.entity.PayOrderEntity;
import io.github.commonpay.entity.PayOrderItemEntity;
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
import io.github.commonpay.mapper.PayNotifyRecordMapper;
import io.github.commonpay.mapper.PayChannelConfigMapper;
import io.github.commonpay.mapper.PayOrderItemMapper;
import io.github.commonpay.mapper.PayOrderMapper;
import io.github.commonpay.mapper.PayRefundOrderMapper;
import io.github.commonpay.mapper.PayStatusRecordMapper;
import io.github.commonpay.mapper.PayTransactionMapper;
import io.github.commonpay.model.PayChannelRequest;
import io.github.commonpay.model.PayChannelResult;
import io.github.commonpay.model.PayClosedContext;
import io.github.commonpay.model.PayCreateItemRequest;
import io.github.commonpay.model.PayExpiredContext;
import io.github.commonpay.model.PaySuccessContext;
import io.github.commonpay.model.RefundSuccessContext;
import io.github.commonpay.service.PayBusinessHandler;
import io.github.commonpay.service.PayReconcileService;
import io.github.commonpay.util.PayIdGenerator;
import io.github.commonpay.util.PayRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class PayReconcileServiceImpl implements PayReconcileService {

    private static final long RECONCILE_LOCK_SECONDS = 60L;

    @Autowired
    private PayOrderMapper payOrderMapper;
    @Autowired
    private PayChannelConfigMapper payChannelConfigMapper;
    @Autowired
    private PayOrderItemMapper payOrderItemMapper;
    @Autowired
    private PayRefundOrderMapper payRefundOrderMapper;
    @Autowired
    private PayNotifyRecordMapper payNotifyRecordMapper;
    @Autowired
    private PayStatusRecordMapper payStatusRecordMapper;
    @Autowired
    private PayTransactionMapper payTransactionMapper;
    @Autowired
    private PayChannelRegistry payChannelRegistry;
    @Autowired
    private PayRedisLock payRedisLock;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired(required = false)
    private List<PayBusinessHandler> payBusinessHandlers = new ArrayList<>();

    @Override
    public int reconcilePayOrders(int batchSize) {
        List<PayOrderEntity> orders = payOrderMapper.selectPage(new Page<>(1, normalizeBatchSize(batchSize)), new LambdaQueryWrapper<PayOrderEntity>()
                .in(PayOrderEntity::getOrderStatus, Arrays.asList(
                        PayOrderStatusEnum.CREATED.name(),
                        PayOrderStatusEnum.WAIT_PAY.name(),
                        PayOrderStatusEnum.PAYING.name(),
                        PayOrderStatusEnum.UNKNOWN.name()))
                .eq(PayOrderEntity::getDeleteMark, 0)
                .orderByAsc(PayOrderEntity::getCreatorTime)).getRecords();
        int count = 0;
        for (PayOrderEntity order : orders) {
            String lockName = orderLockName(order.getPayOrderNo());
            String lockValue = payRedisLock.lock(lockName, RECONCILE_LOCK_SECONDS);
            if (lockValue == null) {
                continue;
            }
            try {
                Boolean changed = transactionTemplate.execute(status -> reconcilePayOrder(order.getPayOrderNo()));
                if (Boolean.TRUE.equals(changed)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("支付订单对账失败，payOrderNo={}", order.getPayOrderNo(), e);
            } finally {
                payRedisLock.unlock(lockName, lockValue);
            }
        }
        return count;
    }

    @Override
    public int reconcileRefundOrders(int batchSize) {
        List<PayRefundOrderEntity> refunds = payRefundOrderMapper.selectPage(new Page<>(1, normalizeBatchSize(batchSize)), new LambdaQueryWrapper<PayRefundOrderEntity>()
                .in(PayRefundOrderEntity::getRefundStatus, Arrays.asList(
                        PayRefundStatusEnum.CREATED.name(),
                        PayRefundStatusEnum.PROCESSING.name(),
                        PayRefundStatusEnum.UNKNOWN.name()))
                .eq(PayRefundOrderEntity::getDeleteMark, 0)
                .orderByAsc(PayRefundOrderEntity::getCreatorTime)).getRecords();
        int count = 0;
        for (PayRefundOrderEntity refund : refunds) {
            String lockName = refundLockName(refund.getRefundNo());
            String lockValue = payRedisLock.lock(lockName, RECONCILE_LOCK_SECONDS);
            if (lockValue == null) {
                continue;
            }
            try {
                Boolean changed = transactionTemplate.execute(status -> reconcileRefundOrder(refund.getRefundNo()));
                if (Boolean.TRUE.equals(changed)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("退款单对账失败，refundNo={}", refund.getRefundNo(), e);
            } finally {
                payRedisLock.unlock(lockName, lockValue);
            }
        }
        return count;
    }

    private boolean reconcilePayOrder(String payOrderNo) {
        // Re-read after acquiring the same order lock used by callbacks so stale scan rows cannot overwrite a late callback.
        PayOrderEntity order = getOrderByNo(payOrderNo);
        if (order.getOrderStatus() == null || PayOrderStatusEnum.valueOf(order.getOrderStatus()).isTerminal()) {
            return false;
        }
        if (expireIfNeeded(order)) {
            return true;
        }
        PayChannelResult result = queryOrder(order);
        saveTransaction(order, PayTradeTypeEnum.QUERY.name(), result.isSuccess() ? PayHandleStatusEnum.SUCCESS.name() : PayHandleStatusEnum.FAILED.name(), null, null, result.getRawResponse(), result.getErrorCode(), result.getErrorMessage());
        return result.isSuccess() && result.getOrderStatus() != null && applyOrderStatus(order, result);
    }

    private boolean reconcileRefundOrder(String refundNo) {
        // Refund callbacks and reconciliation share a refund lock, preventing the refunded amount from being accumulated twice.
        PayRefundOrderEntity refund = getRefundByNo(refundNo);
        if (refund.getRefundStatus() == null || PayRefundStatusEnum.valueOf(refund.getRefundStatus()).isTerminal()) {
            return false;
        }
        PayChannelResult result = queryRefund(refund);
        PayOrderEntity order = getOrderByNo(refund.getPayOrderNo());
        saveTransaction(order, PayTradeTypeEnum.REFUND_QUERY.name(), result.isSuccess() ? PayHandleStatusEnum.SUCCESS.name() : PayHandleStatusEnum.FAILED.name(), refund.getRefundAmount(), null, result.getRawResponse(), result.getErrorCode(), result.getErrorMessage());
        return result.isSuccess() && result.getRefundStatus() != null && applyRefundStatus(refund, order, result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryBusinessNotify(int batchSize, int maxRetryCount) {
        List<PayNotifyRecordEntity> records = payNotifyRecordMapper.selectPage(new Page<>(1, normalizeBatchSize(batchSize)), new LambdaQueryWrapper<PayNotifyRecordEntity>()
                .eq(PayNotifyRecordEntity::getNotifyStatus, PayHandleStatusEnum.SUCCESS.name())
                .eq(PayNotifyRecordEntity::getBusinessStatus, PayHandleStatusEnum.FAILED.name())
                .lt(PayNotifyRecordEntity::getRetryCount, maxRetryCount)
                .eq(PayNotifyRecordEntity::getDeleteMark, 0)
                .orderByAsc(PayNotifyRecordEntity::getLastModifyTime)).getRecords();
        int count = 0;
        for (PayNotifyRecordEntity record : records) {
            try {
                String error = retryBusinessRecord(record);
                record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
                record.setBusinessStatus(isBlank(error) ? PayHandleStatusEnum.SUCCESS.name() : PayHandleStatusEnum.FAILED.name());
                record.setErrorMessage(error);
                touch(record);
                payNotifyRecordMapper.updateById(record);
                if (isBlank(error)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("业务回调补偿失败，notifyNo={}", record.getNotifyNo(), e);
                record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
                record.setErrorMessage(e.getMessage());
                touch(record);
                payNotifyRecordMapper.updateById(record);
            }
        }
        return count;
    }

    private PayChannelResult queryOrder(PayOrderEntity order) {
        PayChannelCodeEnum channelCode = PayChannelCodeEnum.valueOf(order.getChannelCode());
        PayChannelAdapter adapter = payChannelRegistry.get(channelCode);
        PayChannelRequest request = new PayChannelRequest();
        request.setPayAppCode(order.getPayAppCode());
        request.setOrder(order);
        request.setChannelConfig(getEnabledChannelConfig(order.getPayAppCode(), channelCode));
        return adapter.query(request);
    }

    private PayChannelResult queryRefund(PayRefundOrderEntity refund) {
        PayChannelCodeEnum channelCode = PayChannelCodeEnum.valueOf(refund.getChannelCode());
        PayChannelAdapter adapter = payChannelRegistry.get(channelCode);
        PayOrderEntity order = getOrderByNo(refund.getPayOrderNo());
        PayChannelRequest request = new PayChannelRequest();
        request.setPayAppCode(order.getPayAppCode());
        request.setRefundOrder(refund);
        request.setOrder(order);
        request.setChannelConfig(getEnabledChannelConfig(order.getPayAppCode(), channelCode));
        return adapter.queryRefund(request);
    }

    private PayChannelConfigEntity getEnabledChannelConfig(String payAppCode, PayChannelCodeEnum channelCode) {
        return payChannelConfigMapper.selectOne(new LambdaQueryWrapper<PayChannelConfigEntity>()
                .eq(PayChannelConfigEntity::getPayAppCode, payAppCode)
                .eq(PayChannelConfigEntity::getChannelCode, channelCode.name())
                .eq(PayChannelConfigEntity::getEnabledMark, 1)
                .eq(PayChannelConfigEntity::getDeleteMark, 0)
                .orderByAsc(PayChannelConfigEntity::getSortCode)
                .last("limit 1"));
    }

    private boolean expireIfNeeded(PayOrderEntity order) {
        if (order.getExpireTime() == null || order.getExpireTime().after(new Date())) {
            return false;
        }
        if (!PayOrderStatusEnum.WAIT_PAY.name().equals(order.getOrderStatus()) && !PayOrderStatusEnum.PAYING.name().equals(order.getOrderStatus())) {
            return false;
        }
        String beforeStatus = order.getOrderStatus();
        order.setOrderStatus(PayOrderStatusEnum.EXPIRED.name());
        touch(order);
        payOrderMapper.updateById(order);
        updateItemsStatus(order.getId(), PayOrderStatusEnum.EXPIRED.name());
        saveStatus(order, PayObjectTypeEnum.ORDER.name(), beforeStatus, PayOrderStatusEnum.EXPIRED.name(), "RECONCILE_EXPIRE", "定时对账发现支付单过期");
        notifyPayExpired(order);
        return true;
    }

    private boolean applyOrderStatus(PayOrderEntity order, PayChannelResult result) {
        PayOrderStatusEnum status = result.getOrderStatus();
        if (status == null || order.getOrderStatus().equals(status.name())) {
            return false;
        }
        String beforeStatus = order.getOrderStatus();
        if (status == PayOrderStatusEnum.PAID) {
            Long paidAmount = result.getAmount() == null ? order.getTotalAmount() : result.getAmount();
            if (!amountEquals(order.getTotalAmount(), paidAmount)) {
                log.error("支付对账金额不匹配，payOrderNo={}, local={}, channel={}", order.getPayOrderNo(), order.getTotalAmount(), paidAmount);
                return false;
            }
            order.setPaidAmount(paidAmount);
            order.setPayTime(result.getSuccessTime() == null ? new Date() : result.getSuccessTime());
            order.setChannelTradeNo(result.getChannelTradeNo());
            updateItemsStatus(order.getId(), PayOrderStatusEnum.PAID.name());
        }
        if (status == PayOrderStatusEnum.CLOSED || status == PayOrderStatusEnum.CANCELLED || status == PayOrderStatusEnum.EXPIRED) {
            order.setCloseTime(result.getCloseTime() == null ? new Date() : result.getCloseTime());
            updateItemsStatus(order.getId(), status.name());
        }
        order.setOrderStatus(status.name());
        touch(order);
        payOrderMapper.updateById(order);
        saveStatus(order, PayObjectTypeEnum.ORDER.name(), beforeStatus, status.name(), "RECONCILE", "定时对账修正支付状态");
        if (status == PayOrderStatusEnum.PAID) {
            String error = notifyPaySuccess(order);
            if (!isBlank(error)) {
                saveBusinessFailedRecord(order, PayNotifyTypeEnum.PAY.name(), error);
            }
        }
        if (status == PayOrderStatusEnum.CLOSED || status == PayOrderStatusEnum.CANCELLED) {
            notifyPayClosed(order, "定时对账发现订单关闭");
        }
        return true;
    }

    private boolean applyRefundStatus(PayRefundOrderEntity refund, PayOrderEntity order, PayChannelResult result) {
        PayRefundStatusEnum status = result.getRefundStatus();
        if (status == null || refund.getRefundStatus().equals(status.name())) {
            return false;
        }
        String beforeStatus = refund.getRefundStatus();
        refund.setRefundStatus(status.name());
        refund.setChannelRefundNo(result.getChannelRefundNo());
        if (status == PayRefundStatusEnum.SUCCESS) {
            refund.setRefundTime(result.getSuccessTime() == null ? new Date() : result.getSuccessTime());
        }
        touch(refund);
        payRefundOrderMapper.updateById(refund);
        saveRefundStatus(refund, beforeStatus, status.name(), "RECONCILE", "定时对账修正退款状态");
        if (status == PayRefundStatusEnum.SUCCESS) {
            long newRefundAmount = safeAmount(order.getRefundAmount()) + safeAmount(refund.getRefundAmount());
            order.setRefundAmount(newRefundAmount);
            order.setOrderStatus(newRefundAmount >= safeAmount(order.getPaidAmount()) ? PayOrderStatusEnum.REFUNDED.name() : PayOrderStatusEnum.PARTIAL_REFUND.name());
            touch(order);
            payOrderMapper.updateById(order);
            String error = notifyRefundSuccess(refund);
            if (!isBlank(error)) {
                saveBusinessFailedRecord(refund, PayNotifyTypeEnum.REFUND.name(), error);
            }
        }
        return true;
    }

    private String retryBusinessRecord(PayNotifyRecordEntity record) {
        if (PayNotifyTypeEnum.PAY.name().equals(record.getNotifyType())) {
            PayOrderEntity order = getOrderByNo(record.getPayOrderNo());
            if (!PayOrderStatusEnum.PAID.name().equals(order.getOrderStatus())) {
                return "支付订单不是已支付状态";
            }
            return notifyPaySuccess(order);
        }
        if (PayNotifyTypeEnum.REFUND.name().equals(record.getNotifyType())) {
            PayRefundOrderEntity refund = getRefundByNo(record.getRefundNo());
            if (!PayRefundStatusEnum.SUCCESS.name().equals(refund.getRefundStatus())) {
                return "退款单不是退款成功状态";
            }
            return notifyRefundSuccess(refund);
        }
        return "未知回调类型";
    }

    private void saveBusinessFailedRecord(PayOrderEntity order, String notifyType, String error) {
        PayNotifyRecordEntity record = new PayNotifyRecordEntity();
        fillCreate(record, new Date());
        record.setNotifyNo(generateNo("NTF"));
        record.setNotifyType(notifyType);
        record.setChannelCode(order.getChannelCode());
        record.setPayOrderNo(order.getPayOrderNo());
        record.setChannelTradeNo(order.getChannelTradeNo());
        record.setNotifyStatus(PayHandleStatusEnum.SUCCESS.name());
        record.setBusinessStatus(PayHandleStatusEnum.FAILED.name());
        record.setErrorMessage(error);
        record.setNotifyTime(new Date());
        record.setHandleTime(new Date());
        record.setRetryCount(0);
        payNotifyRecordMapper.insert(record);
    }

    private void saveBusinessFailedRecord(PayRefundOrderEntity refund, String notifyType, String error) {
        PayNotifyRecordEntity record = new PayNotifyRecordEntity();
        fillCreate(record, new Date());
        record.setNotifyNo(generateNo("NTF"));
        record.setNotifyType(notifyType);
        record.setChannelCode(refund.getChannelCode());
        record.setPayOrderNo(refund.getPayOrderNo());
        record.setRefundNo(refund.getRefundNo());
        record.setChannelTradeNo(refund.getChannelRefundNo());
        record.setNotifyStatus(PayHandleStatusEnum.SUCCESS.name());
        record.setBusinessStatus(PayHandleStatusEnum.FAILED.name());
        record.setErrorMessage(error);
        record.setNotifyTime(new Date());
        record.setHandleTime(new Date());
        record.setRetryCount(0);
        payNotifyRecordMapper.insert(record);
    }

    private void updateItemsStatus(String payOrderId, String status) {
        List<PayOrderItemEntity> items = payOrderItemMapper.selectList(new LambdaQueryWrapper<PayOrderItemEntity>()
                .eq(PayOrderItemEntity::getPayOrderId, payOrderId)
                .eq(PayOrderItemEntity::getDeleteMark, 0));
        for (PayOrderItemEntity item : items) {
            item.setItemStatus(status);
            if (PayOrderStatusEnum.PAID.name().equals(status)) {
                item.setPaidAmount(item.getItemAmount());
            }
            touch(item);
            payOrderItemMapper.updateById(item);
        }
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

    private void saveRefundStatus(PayRefundOrderEntity refund, String beforeStatus, String afterStatus, String changeType, String reason) {
        PayStatusRecordEntity record = new PayStatusRecordEntity();
        fillCreate(record, new Date());
        record.setObjectType(PayObjectTypeEnum.REFUND.name());
        record.setObjectId(refund.getId());
        record.setObjectNo(refund.getRefundNo());
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
                    log.error("业务支付成功补偿失败，payOrderNo={}", order.getPayOrderNo(), e);
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

    private String notifyRefundSuccess(PayRefundOrderEntity refund) {
        String error = null;
        for (PayBusinessHandler handler : payBusinessHandlers) {
            if (handler.supports(refund.getBizType())) {
                try {
                    RefundSuccessContext context = new RefundSuccessContext();
                    context.setRefundNo(refund.getRefundNo());
                    context.setPayOrderNo(refund.getPayOrderNo());
                    context.setChannelCode(refund.getChannelCode());
                    context.setChannelRefundNo(refund.getChannelRefundNo());
                    context.setBizType(refund.getBizType());
                    context.setRefTable(refund.getRefTable());
                    context.setRefValue(refund.getRefValue());
                    context.setRefNo(refund.getRefNo());
                    context.setRefundAmount(refund.getRefundAmount());
                    context.setRefundTime(refund.getRefundTime());
                    handler.onRefundSuccess(context);
                } catch (Exception e) {
                    log.error("业务退款成功补偿失败，refundNo={}", refund.getRefundNo(), e);
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

    private PayOrderEntity getOrderByNo(String payOrderNo) {
        List<PayOrderEntity> orders = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderEntity>()
                .eq(PayOrderEntity::getPayOrderNo, payOrderNo)
                .eq(PayOrderEntity::getDeleteMark, 0));
        if (orders == null || orders.isEmpty()) {
            throw new IllegalStateException("支付订单不存在");
        }
        if (orders.size() > 1) {
            throw new IllegalStateException("支付订单号重复，请检查数据");
        }
        return orders.get(0);
    }

    private PayRefundOrderEntity getRefundByNo(String refundNo) {
        List<PayRefundOrderEntity> refunds = payRefundOrderMapper.selectList(new LambdaQueryWrapper<PayRefundOrderEntity>()
                .eq(PayRefundOrderEntity::getRefundNo, refundNo)
                .eq(PayRefundOrderEntity::getDeleteMark, 0));
        if (refunds == null || refunds.isEmpty()) {
            throw new IllegalStateException("退款单不存在");
        }
        if (refunds.size() > 1) {
            throw new IllegalStateException("退款单号重复，请检查数据");
        }
        return refunds.get(0);
    }

    private int normalizeBatchSize(int batchSize) {
        return batchSize <= 0 ? 100 : Math.min(batchSize, 1000);
    }

    private boolean amountEquals(Long expected, Long actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.longValue() == actual.longValue();
    }

    private long safeAmount(Long amount) {
        return amount == null ? 0L : amount;
    }

    private void fillCreate(BasePayEntity entity, Date now) {
        entity.setId(PayIdGenerator.nextId());
        entity.setCreatorTime(now);
        entity.setLastModifyTime(now);
        entity.setDeleteMark(0);
    }

    private void touch(BasePayEntity entity) {
        entity.setLastModifyTime(new Date());
    }

    private String generateNo(String prefix) {
        return prefix + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + PayIdGenerator.nextId();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package io.github.commonpay.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.github.commonpay.channel.PayChannelAdapter;
import io.github.commonpay.entity.PayNotifyRecordEntity;
import io.github.commonpay.entity.PayOrderEntity;
import io.github.commonpay.entity.PayRefundOrderEntity;
import io.github.commonpay.entity.PayTransactionEntity;
import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.enums.PayOrderStatusEnum;
import io.github.commonpay.enums.PayRefundStatusEnum;
import io.github.commonpay.mapper.PayNotifyRecordMapper;
import io.github.commonpay.mapper.PayOrderItemMapper;
import io.github.commonpay.mapper.PayOrderMapper;
import io.github.commonpay.mapper.PayRefundOrderMapper;
import io.github.commonpay.mapper.PayStatusRecordMapper;
import io.github.commonpay.mapper.PayTransactionMapper;
import io.github.commonpay.model.PayNotifyMessage;
import io.github.commonpay.service.PayBusinessHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonPayServiceCallbackTest {

    private CommonPayServiceImpl service;
    private PayOrderMapper orderMapper;
    private PayRefundOrderMapper refundMapper;
    private PayNotifyRecordMapper notifyMapper;
    private PayOrderItemMapper itemMapper;
    private PayTransactionMapper transactionMapper;
    private PayStatusRecordMapper statusMapper;
    private PayBusinessHandler businessHandler;
    private PayChannelAdapter adapter;

    @BeforeEach
    void setUp() {
        service = new CommonPayServiceImpl();
        orderMapper = mock(PayOrderMapper.class);
        refundMapper = mock(PayRefundOrderMapper.class);
        notifyMapper = mock(PayNotifyRecordMapper.class);
        itemMapper = mock(PayOrderItemMapper.class);
        transactionMapper = mock(PayTransactionMapper.class);
        statusMapper = mock(PayStatusRecordMapper.class);
        businessHandler = mock(PayBusinessHandler.class);
        adapter = mock(PayChannelAdapter.class);

        ReflectionTestUtils.setField(service, "payOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "payRefundOrderMapper", refundMapper);
        ReflectionTestUtils.setField(service, "payNotifyRecordMapper", notifyMapper);
        ReflectionTestUtils.setField(service, "payOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "payTransactionMapper", transactionMapper);
        ReflectionTestUtils.setField(service, "payStatusRecordMapper", statusMapper);
        ReflectionTestUtils.setField(service, "payBusinessHandlers", Collections.singletonList(businessHandler));

        when(adapter.buildPayNotifySuccessResponse()).thenReturn("success");
        when(adapter.buildPayNotifyFailResponse(any(String.class))).thenReturn("fail");
        when(adapter.buildRefundNotifySuccessResponse()).thenReturn("success");
        when(adapter.buildRefundNotifyFailResponse(any(String.class))).thenReturn("fail");
        when(notifyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(businessHandler.supports("ORDER")).thenReturn(true);
    }

    @Test
    void duplicatePaymentCallbackDoesNotRepeatBusinessEffects() {
        PayOrderEntity order = waitingOrder();
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(order));
        PayNotifyMessage message = successfulMessage("PAY-1001", 1200L);

        assertEquals("success", service.handlePayNotifyLocked(adapter, PayChannelCodeEnum.ALIPAY, message));
        assertEquals("success", service.handlePayNotifyLocked(adapter, PayChannelCodeEnum.ALIPAY, message));

        assertEquals(PayOrderStatusEnum.PAID.name(), order.getOrderStatus());
        verify(orderMapper, times(1)).updateById(order);
        verify(transactionMapper, times(1)).insert(any(PayTransactionEntity.class));
        verify(businessHandler, times(1)).onPaySuccess(any());
        verify(notifyMapper, times(2)).insert(any(PayNotifyRecordEntity.class));
    }

    @Test
    void existingSuccessfulProviderNotificationIsAcknowledgedWithoutAnotherInsert() {
        PayOrderEntity order = waitingOrder();
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(order));
        PayNotifyRecordEntity recorded = new PayNotifyRecordEntity();
        recorded.setNotifyType("PAY");
        recorded.setNotifyStatus("SUCCESS");
        when(notifyMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.emptyList(), Collections.singletonList(recorded));
        PayNotifyMessage message = successfulMessage("PAY-1001", 1200L);
        message.setChannelNotifyId("provider-notify-1");

        assertEquals("success", service.handlePayNotifyLocked(adapter, PayChannelCodeEnum.ALIPAY, message));
        assertEquals("success", service.handlePayNotifyLocked(adapter, PayChannelCodeEnum.ALIPAY, message));

        verify(notifyMapper, times(1)).insert(any(PayNotifyRecordEntity.class));
        verify(businessHandler, times(1)).onPaySuccess(any());
    }

    @Test
    void invalidSignatureCannotChangePaymentState() {
        PayOrderEntity order = waitingOrder();
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(order));
        PayNotifyMessage message = successfulMessage("PAY-1001", 1200L);
        message.setVerifySuccess(false);

        assertEquals("fail", service.handlePayNotifyLocked(adapter, PayChannelCodeEnum.ALIPAY, message));

        assertEquals(PayOrderStatusEnum.WAIT_PAY.name(), order.getOrderStatus());
        verify(orderMapper, never()).updateById(any(PayOrderEntity.class));
        verify(businessHandler, never()).onPaySuccess(any());
    }

    @Test
    void callbackFromAnotherChannelCannotChangePaymentState() {
        PayOrderEntity order = waitingOrder();
        order.setChannelCode(PayChannelCodeEnum.WECHAT.name());
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(order));

        assertEquals("fail", service.handlePayNotifyLocked(
                adapter, PayChannelCodeEnum.ALIPAY, successfulMessage("PAY-1001", 1200L)));

        verify(orderMapper, never()).updateById(any(PayOrderEntity.class));
    }

    @Test
    void duplicateRefundCallbackDoesNotIncreaseRefundTwice() {
        PayOrderEntity order = waitingOrder();
        order.setOrderStatus(PayOrderStatusEnum.REFUNDING.name());
        order.setPaidAmount(1200L);
        order.setRefundAmount(0L);
        PayRefundOrderEntity refund = new PayRefundOrderEntity();
        refund.setId("refund-id");
        refund.setRefundNo("RFD-1001");
        refund.setPayOrderNo(order.getPayOrderNo());
        refund.setBizType("ORDER");
        refund.setChannelCode(PayChannelCodeEnum.ALIPAY.name());
        refund.setRefundAmount(300L);
        refund.setRefundStatus(PayRefundStatusEnum.PROCESSING.name());
        when(refundMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(refund));
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(order));
        PayNotifyMessage message = successfulMessage(order.getPayOrderNo(), 300L);
        message.setRefundNo(refund.getRefundNo());
        message.setChannelRefundNo("CHANNEL-RFD-1001");

        assertEquals("success", service.handleRefundNotifyLocked(adapter, PayChannelCodeEnum.ALIPAY, message));
        assertEquals("success", service.handleRefundNotifyLocked(adapter, PayChannelCodeEnum.ALIPAY, message));

        assertEquals(Long.valueOf(300L), order.getRefundAmount());
        verify(orderMapper, times(1)).updateById(order);
        verify(refundMapper, times(1)).updateById(refund);
        verify(businessHandler, times(1)).onRefundSuccess(any());
    }

    private PayOrderEntity waitingOrder() {
        PayOrderEntity order = new PayOrderEntity();
        order.setId("order-id");
        order.setPayOrderNo("PAY-1001");
        order.setBizType("ORDER");
        order.setChannelCode(PayChannelCodeEnum.ALIPAY.name());
        order.setTotalAmount(1200L);
        order.setPaidAmount(0L);
        order.setRefundAmount(0L);
        order.setOrderStatus(PayOrderStatusEnum.WAIT_PAY.name());
        return order;
    }

    private PayNotifyMessage successfulMessage(String payOrderNo, long amount) {
        PayNotifyMessage message = new PayNotifyMessage();
        message.setPayOrderNo(payOrderNo);
        message.setAmount(amount);
        message.setVerifySuccess(true);
        message.setSuccess(true);
        message.setSuccessTime(new Date());
        message.setChannelTradeNo("CHANNEL-1001");
        message.setRawBody("{\"synthetic\":true}");
        return message;
    }
}

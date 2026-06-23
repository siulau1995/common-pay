package io.github.commonpay.enums;

/**
 * 支付订单生命周期状态。
 */
public enum PayOrderStatusEnum {
    CREATED,
    WAIT_PAY,
    PAYING,
    PAID,
    PARTIAL_REFUND,
    REFUNDING,
    REFUNDED,
    CLOSED,
    CANCELLED,
    EXPIRED,
    FAILED,
    UNKNOWN;

    public boolean isTerminal() {
        return this == PAID || this == REFUNDED || this == CLOSED || this == CANCELLED || this == EXPIRED || this == FAILED;
    }
}

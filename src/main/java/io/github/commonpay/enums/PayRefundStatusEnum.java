package io.github.commonpay.enums;

/**
 * 退款单状态。
 */
public enum PayRefundStatusEnum {
    CREATED,
    PROCESSING,
    SUCCESS,
    FAILED,
    CLOSED,
    UNKNOWN;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CLOSED;
    }
}

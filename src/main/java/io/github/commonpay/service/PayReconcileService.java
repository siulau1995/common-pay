package io.github.commonpay.service;

public interface PayReconcileService {

    int reconcilePayOrders(int batchSize);

    int reconcileRefundOrders(int batchSize);

    int retryBusinessNotify(int batchSize, int maxRetryCount);
}

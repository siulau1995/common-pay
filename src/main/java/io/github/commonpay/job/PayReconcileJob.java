package io.github.commonpay.job;

import io.github.commonpay.service.PayReconcileService;
import io.github.commonpay.tenant.PayTenantDataSourceSwitcher;
import io.github.commonpay.util.PayRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "common.pay.reconcile", name = "enabled", havingValue = "true")
public class PayReconcileJob {

    @Autowired
    private PayReconcileService payReconcileService;
    @Autowired
    private PayRedisLock payRedisLock;
    @Autowired
    private PayTenantDataSourceSwitcher tenantDataSourceSwitcher;

    @Value("${common.pay.reconcile.tenant-ids:}")
    private String tenantIds;
    @Value("${common.pay.reconcile.batch-size:100}")
    private int batchSize;
    @Value("${common.pay.reconcile.max-business-retry:5}")
    private int maxBusinessRetry;

    @Scheduled(fixedDelayString = "${common.pay.reconcile.pay-fixed-delay:300000}")
    public void reconcilePayOrders() {
        runForTenants("pay-order", () -> payReconcileService.reconcilePayOrders(batchSize));
    }

    @Scheduled(fixedDelayString = "${common.pay.reconcile.refund-fixed-delay:300000}")
    public void reconcileRefundOrders() {
        runForTenants("pay-refund", () -> payReconcileService.reconcileRefundOrders(batchSize));
    }

    @Scheduled(fixedDelayString = "${common.pay.reconcile.business-retry-fixed-delay:120000}")
    public void retryBusinessNotify() {
        runForTenants("pay-business-retry", () -> payReconcileService.retryBusinessNotify(batchSize, maxBusinessRetry));
    }

    private void runForTenants(String jobName, ReconcileRunner runner) {
        String[] tenants = tenantIds == null || tenantIds.trim().isEmpty() ? new String[]{""} : tenantIds.split(",");
        for (String tenantId : tenants) {
            String normalizedTenantId = tenantId == null ? "" : tenantId.trim();
            String lockName = jobName + ":" + (normalizedTenantId.isEmpty() ? "default" : normalizedTenantId);
            String lockValue = payRedisLock.lock(lockName, 300);
            if (lockValue == null) {
                continue;
            }
            try {
                if (!normalizedTenantId.isEmpty()) {
                    tenantDataSourceSwitcher.use(normalizedTenantId);
                }
                int count = runner.run();
                if (count > 0) {
                    log.info("支付对账任务完成，job={}, tenantId={}, count={}", jobName, normalizedTenantId, count);
                }
            } catch (Exception e) {
                log.error("支付对账任务失败，job={}, tenantId={}", jobName, normalizedTenantId, e);
            } finally {
                tenantDataSourceSwitcher.clear();
                payRedisLock.unlock(lockName, lockValue);
            }
        }
    }

    private interface ReconcileRunner {
        int run();
    }
}

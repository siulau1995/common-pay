package io.github.commonpay.controller;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.service.CommonPayService;
import io.github.commonpay.tenant.PayTenantDataSourceSwitcher;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonPayControllerTenantTest {

    @Test
    void clearsTenantContextWhenCallbackFails() {
        CommonPayService service = mock(CommonPayService.class);
        PayTenantDataSourceSwitcher switcher = mock(PayTenantDataSourceSwitcher.class);
        when(service.handlePayNotify(anyString(), any(PayChannelCodeEnum.class), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("synthetic failure"));
        CommonPayController controller = new CommonPayController(service, switcher);

        String response = controller.payNotify(
                "tenant-a", "ALIPAY", "DEFAULT_PAY", Collections.emptyMap(), Collections.emptyMap(), "{}");

        assertEquals("fail", response);
        verify(switcher).use("tenant-a");
        verify(switcher).clear();
    }
}

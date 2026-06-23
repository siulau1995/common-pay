package io.github.commonpay.channel;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.util.PayException;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayChannelRegistryTest {

    @Test
    void resolvesRegisteredAdapter() {
        PayChannelAdapter adapter = mock(PayChannelAdapter.class);
        when(adapter.getChannelCode()).thenReturn(PayChannelCodeEnum.ALIPAY);
        PayChannelRegistry registry = new PayChannelRegistry(Collections.singletonList(adapter));

        assertSame(adapter, registry.get(PayChannelCodeEnum.ALIPAY));
    }

    @Test
    void rejectsUnregisteredChannel() {
        PayChannelRegistry registry = new PayChannelRegistry(Collections.emptyList());

        assertThrows(PayException.class, () -> registry.get(PayChannelCodeEnum.WECHAT));
    }
}

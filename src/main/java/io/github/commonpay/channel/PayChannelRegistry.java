package io.github.commonpay.channel;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.util.PayException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PayChannelRegistry {

    private final Map<PayChannelCodeEnum, PayChannelAdapter> adapterMap = new EnumMap<>(PayChannelCodeEnum.class);

    public PayChannelRegistry(List<PayChannelAdapter> adapters) {
        for (PayChannelAdapter adapter : adapters) {
            adapterMap.put(adapter.getChannelCode(), adapter);
        }
    }

    public PayChannelAdapter get(PayChannelCodeEnum channelCode) {
        PayChannelAdapter adapter = adapterMap.get(channelCode);
        if (adapter == null) {
            throw new PayException("未找到支付渠道适配器：" + channelCode);
        }
        return adapter;
    }
}

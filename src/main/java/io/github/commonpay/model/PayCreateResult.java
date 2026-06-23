package io.github.commonpay.model;

import io.github.commonpay.enums.PayChannelCodeEnum;
import io.github.commonpay.enums.PaySceneEnum;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class PayCreateResult {
    private String payOrderNo;
    private PayChannelCodeEnum channelCode;
    private PaySceneEnum payScene;
    private String codeUrl;
    private String payForm;
    private Map<String, Object> payParams = new HashMap<>();
}

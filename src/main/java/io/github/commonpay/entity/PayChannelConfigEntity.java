package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_channel_config")
public class PayChannelConfigEntity extends BasePayEntity {

    @TableField("F_Pay_App_Code")
    private String payAppCode;

    @TableField("F_Pay_App_Name")
    private String payAppName;

    @TableField("F_Channel_Code")
    private String channelCode;

    @TableField("F_Merchant_Id")
    private String merchantId;

    @TableField("F_App_Id")
    private String appId;

    @TableField("F_Environment")
    private String environment;

    @TableField("F_Notify_Url")
    private String notifyUrl;

    @TableField("F_Refund_Notify_Url")
    private String refundNotifyUrl;

    @TableField("F_Config_Json")
    private String configJson;

    @TableField("F_Enabled_Mark")
    private Integer enabledMark;

    @TableField("F_Sort_Code")
    private Long sortCode;
}

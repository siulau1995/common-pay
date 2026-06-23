package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_notify_record")
public class PayNotifyRecordEntity extends BasePayEntity {

    @TableField("F_Notify_No")
    private String notifyNo;

    @TableField("F_Notify_Type")
    private String notifyType;

    @TableField("F_Channel_Code")
    private String channelCode;

    @TableField("F_Pay_Order_No")
    private String payOrderNo;

    @TableField("F_Refund_No")
    private String refundNo;

    @TableField("F_Channel_Notify_Id")
    private String channelNotifyId;

    @TableField("F_Channel_Trade_No")
    private String channelTradeNo;

    @TableField("F_Notify_Status")
    private String notifyStatus;

    @TableField("F_Verify_Status")
    private String verifyStatus;

    @TableField("F_Business_Status")
    private String businessStatus;

    @TableField("F_Notify_Body")
    private String notifyBody;

    @TableField("F_Response_Body")
    private String responseBody;

    @TableField("F_Error_Message")
    private String errorMessage;

    @TableField("F_Notify_Time")
    private Date notifyTime;

    @TableField("F_Handle_Time")
    private Date handleTime;

    @TableField("F_Retry_Count")
    private Integer retryCount;
}

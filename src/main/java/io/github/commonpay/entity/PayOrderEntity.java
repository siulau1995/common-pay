package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_order")
public class PayOrderEntity extends BasePayEntity {

    @TableField("F_Pay_Order_No")
    private String payOrderNo;

    @TableField("F_Biz_Type")
    private String bizType;

    @TableField("F_Ref_Table")
    private String refTable;

    @TableField("F_Ref_Value")
    private String refValue;

    @TableField("F_Ref_No")
    private String refNo;

    @TableField("F_Pay_App_Code")
    private String payAppCode;

    @TableField("F_Channel_Code")
    private String channelCode;

    @TableField("F_Subject")
    private String subject;

    @TableField("F_Body")
    private String body;

    @TableField("F_Total_Amount")
    private Long totalAmount;

    @TableField("F_Paid_Amount")
    private Long paidAmount;

    @TableField("F_Refund_Amount")
    private Long refundAmount;

    @TableField("F_Currency")
    private String currency;

    @TableField("F_Order_Status")
    private String orderStatus;

    @TableField("F_Pay_Scene")
    private String payScene;

    @TableField("F_Client_Ip")
    private String clientIp;

    @TableField("F_Open_Id")
    private String openId;

    @TableField("F_Expire_Time")
    private Date expireTime;

    @TableField("F_Pay_Time")
    private Date payTime;

    @TableField("F_Close_Time")
    private Date closeTime;

    @TableField("F_Channel_Trade_No")
    private String channelTradeNo;

    @TableField("F_Channel_Order_No")
    private String channelOrderNo;

    @TableField("F_Extra_Json")
    private String extraJson;
}

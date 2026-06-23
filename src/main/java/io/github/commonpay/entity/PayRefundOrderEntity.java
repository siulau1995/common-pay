package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_refund_order")
public class PayRefundOrderEntity extends BasePayEntity {

    @TableField("F_Refund_No")
    private String refundNo;

    @TableField("F_Pay_Order_Id")
    private String payOrderId;

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

    @TableField("F_Channel_Code")
    private String channelCode;

    @TableField("F_Refund_Amount")
    private Long refundAmount;

    @TableField("F_Refund_Reason")
    private String refundReason;

    @TableField("F_Refund_Status")
    private String refundStatus;

    @TableField("F_Channel_Refund_No")
    private String channelRefundNo;

    @TableField("F_Refund_Time")
    private Date refundTime;

    @TableField("F_Extra_Json")
    private String extraJson;
}

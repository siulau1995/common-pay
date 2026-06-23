package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_refund_item")
public class PayRefundItemEntity extends BasePayEntity {

    @TableField("F_Refund_Order_Id")
    private String refundOrderId;

    @TableField("F_Refund_No")
    private String refundNo;

    @TableField("F_Pay_Order_Item_Id")
    private String payOrderItemId;

    @TableField("F_Ref_Table")
    private String refTable;

    @TableField("F_Ref_Value")
    private String refValue;

    @TableField("F_Ref_No")
    private String refNo;

    @TableField("F_Refund_Amount")
    private Long refundAmount;

    @TableField("F_Refund_Status")
    private String refundStatus;

    @TableField("F_Extra_Json")
    private String extraJson;
}

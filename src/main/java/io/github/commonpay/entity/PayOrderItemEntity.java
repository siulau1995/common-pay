package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_order_item")
public class PayOrderItemEntity extends BasePayEntity {

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

    @TableField("F_Item_Name")
    private String itemName;

    @TableField("F_Item_Amount")
    private Long itemAmount;

    @TableField("F_Paid_Amount")
    private Long paidAmount;

    @TableField("F_Refund_Amount")
    private Long refundAmount;

    @TableField("F_Item_Status")
    private String itemStatus;

    @TableField("F_Extra_Json")
    private String extraJson;
}

package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_status_record")
public class PayStatusRecordEntity extends BasePayEntity {

    @TableField("F_Object_Type")
    private String objectType;

    @TableField("F_Object_Id")
    private String objectId;

    @TableField("F_Object_No")
    private String objectNo;

    @TableField("F_Before_Status")
    private String beforeStatus;

    @TableField("F_After_Status")
    private String afterStatus;

    @TableField("F_Change_Type")
    private String changeType;

    @TableField("F_Change_Reason")
    private String changeReason;

    @TableField("F_Operator_Type")
    private String operatorType;

    @TableField("F_Operator_Id")
    private String operatorId;

    @TableField("F_Extra_Json")
    private String extraJson;
}

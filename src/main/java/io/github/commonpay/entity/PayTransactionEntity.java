package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_pay_transaction")
public class PayTransactionEntity extends BasePayEntity {

    @TableField("F_Pay_Order_Id")
    private String payOrderId;

    @TableField("F_Pay_Order_No")
    private String payOrderNo;

    @TableField("F_Transaction_No")
    private String transactionNo;

    @TableField("F_Channel_Code")
    private String channelCode;

    @TableField("F_Trade_Type")
    private String tradeType;

    @TableField("F_Trade_Status")
    private String tradeStatus;

    @TableField("F_Amount")
    private Long amount;

    @TableField("F_Channel_Trade_No")
    private String channelTradeNo;

    @TableField("F_Request_Body")
    private String requestBody;

    @TableField("F_Response_Body")
    private String responseBody;

    @TableField("F_Error_Code")
    private String errorCode;

    @TableField("F_Error_Message")
    private String errorMessage;

    @TableField("F_Request_Time")
    private Date requestTime;

    @TableField("F_Response_Time")
    private Date responseTime;
}

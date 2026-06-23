package io.github.commonpay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Common persistence fields owned by this module.
 *
 * <p>The host application may populate user-related fields. Tenant selection is
 * deliberately handled outside the entity so the module can work with a normal
 * datasource or a host-provided multi-tenant datasource.</p>
 */
@Data
public abstract class BasePayEntity implements Serializable {

    @TableId(value = "F_Id", type = IdType.INPUT)
    private String id;

    @TableField("F_Creator_Time")
    private Date creatorTime;

    @TableField("F_Creator_User_Id")
    private String creatorUserId;

    @TableField("F_Last_Modify_Time")
    private Date lastModifyTime;

    @TableField("F_Last_Modify_User_Id")
    private String lastModifyUserId;

    @TableField("F_Delete_Mark")
    private Integer deleteMark;

    @TableField("F_Delete_Time")
    private Date deleteTime;

    @TableField("F_Delete_User_Id")
    private String deleteUserId;

    @TableField("F_Description")
    private String description;
}

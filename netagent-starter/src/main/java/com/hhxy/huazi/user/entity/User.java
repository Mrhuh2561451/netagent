package com.hhxy.huazi.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * 系统用户表实体类，对应表 t_user
 */
@Data
@Builder
@TableName("t_user")
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 用户名，唯一
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 角色：admin/user
     */
    private String role;

    /**
     * 用户头像
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatar;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 是否删除 0：正常 1：删除
     */
    @TableLogic
    private Integer deleted;
}

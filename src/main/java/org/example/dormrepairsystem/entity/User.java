package org.example.dormrepairsystem.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类（学生/维修人员/管理员）
 */
@TableName("user")
@Data
@Schema(name = "User", description = "用户实体类")
public class User {
    /**
     * 用户主键ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "用户主键ID")
    private Long userId;

    /**
     * 账号：学生学号（3125/3225前缀）/工号（0025前缀）
     */
    @Schema(description = "账号")
    private String account;

    /**
     * 密码（BCrypt 加密存储；只允许写入，序列化响应时不会返回给前端）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "密码")
    private String password;

    /**
     * 关联角色表主键ID
     */
    @Schema(description = "关联角色表主键ID")
    private Integer roleId;

    /**
     * 用户姓名
     */
    @Schema(description = "用户姓名")
    private String userName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
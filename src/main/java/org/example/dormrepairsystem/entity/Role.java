package org.example.dormrepairsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色实体类
 */
@TableName("role")
@Data
@Schema(name = "Role", description = "角色实体类")
public class Role {
    /**
     * 角色主键ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "角色主键ID")
    private Integer roleId;

    /**
     * 角色名称：学生/维修人员/管理员
     */
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色编码：student/repairman/admin
     */
    @Schema(description = "角色编码")
    private String roleCode;

    /**
     * 创建时间（数据库默认当前时间）
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间（数据库自动更新）
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
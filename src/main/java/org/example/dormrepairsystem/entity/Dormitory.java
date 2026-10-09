package org.example.dormrepairsystem.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 宿舍绑定实体类
 */
@TableName("dormitory")
@Data
@Schema(name = "Dormitory", description = "宿舍绑定实体类")
public class Dormitory {
    /**
     * 宿舍主键ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "宿舍主键ID")
    private Long dormId;

    /**
     * 关联用户表主键ID（仅学生）
     */
    @Schema(description = "关联用户表主键ID")
    private Long userId;

    /**
     * 宿舍楼栋：如1栋/东区5栋
     */
    @Schema(description = "宿舍楼栋")
    private String building;

    /**
     * 房间号：如502/301-2
     */
    @Schema(description = "房间号")
    private String roomNum;

    /**
     * 绑定时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "绑定时间")
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "修改时间")
    private LocalDateTime updateTime;
}
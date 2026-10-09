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
 * 报修单实体类
 */
@TableName("repair_order")
@Data
@Schema(name = "RepairOrder", description = "报修单实体类")
public class RepairOrder {
    /**
     * 报修单主键ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "报修单主键ID")
    private Long orderId;

    /**
     * 关联报修用户ID
     */
    @Schema(description = "关联报修用户ID")
    private Long userId;

    /**
     * 关联宿舍ID（快速定位报修地址）
     */
    @Schema(description = "关联宿舍ID")
    private Long dormId;
    
    /**
     * 关联维修人员ID
     */
    @Schema(description = "关联维修人员ID")
    private Long repairmanId;

    /**
     * 楼栋
     */
    @Schema(description = "楼栋")
    private String building;

    /**
     * 房间号
     */
    @Schema(description = "房间号")
    private String roomNum;

    /**
     * 设备类型：如水龙头/电灯/空调/马桶
     */
    @Schema(description = "设备类型")
    private String deviceType;

    /**
     * 问题描述
     */
    @Schema(description = "问题描述")
    private String problemDesc;

    /**
     * 报修单状态：待处理/维修中/已完成/已取消
     */
    @Schema(description = "报修单状态")
    private String orderStatus;

    /**
     * 报修创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "报修创建时间")
    private LocalDateTime createTime;

    /**
     * 状态最后修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "状态最后修改时间")
    private LocalDateTime updateTime;
}
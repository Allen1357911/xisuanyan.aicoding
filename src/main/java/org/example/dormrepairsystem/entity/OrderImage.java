package org.example.dormrepairsystem.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_image")
@Schema(name = "OrderImage", description = "订单图片实体类")
public class OrderImage {
    
    @TableId(type = IdType.AUTO)
    @Schema(description = "图片主键ID")
    private Long id;
    
    @TableField("order_id")
    @Schema(description = "关联订单ID")
    private Long orderId;
    
    @TableField("image_url")
    @Schema(description = "图片URL")
    private String imageUrl;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
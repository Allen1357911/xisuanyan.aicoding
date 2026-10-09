package org.example.dormrepairsystem.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 包含用户信息的订单DTO
 */
@Data
public class OrderWithUserDTO {
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 学生姓名
     */
    private String userName;
    
    /**
     * 宿舍ID
     */
    private Long dormId;
    
    /**
     * 楼栋
     */
    private String building;
    
    /**
     * 房间号
     */
    private String roomNum;
    
    /**
     * 维修人员ID
     */
    private Long repairmanId;
    
    /**
     * 维修人员姓名
     */
    private String repairmanName;
    
    /**
     * 设备类型
     */
    private String deviceType;
    
    /**
     * 问题描述
     */
    private String problemDesc;
    
    /**
     * 订单状态
     */
    private String orderStatus;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
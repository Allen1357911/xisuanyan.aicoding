package org.example.dormrepairsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.dormrepairsystem.entity.OrderImage;

import java.util.List;

public interface OrderImageService extends IService<OrderImage> {
    
    /**
     * 根据订单ID查询图片列表
     */
    List<OrderImage> getByOrderId(Long orderId);
    
    /**
     * 保存图片信息
     */
    boolean saveOrderImage(OrderImage orderImage);
    
    /**
     * 根据订单ID删除图片
     */
    boolean deleteByOrderId(Long orderId);
}
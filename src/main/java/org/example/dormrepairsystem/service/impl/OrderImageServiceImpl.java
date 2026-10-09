package org.example.dormrepairsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.dormrepairsystem.entity.OrderImage;
import org.example.dormrepairsystem.mapper.OrderImageMapper;
import org.example.dormrepairsystem.service.OrderImageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderImageServiceImpl extends ServiceImpl<OrderImageMapper, OrderImage> implements OrderImageService {
    
    @Override
    public List<OrderImage> getByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderImage::getOrderId, orderId);
        return baseMapper.selectList(queryWrapper);
    }
    
    @Override
    public boolean saveOrderImage(OrderImage orderImage) {
        return save(orderImage);
    }
    
    @Override
    public boolean deleteByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderImage::getOrderId, orderId);
        return remove(queryWrapper);
    }
}
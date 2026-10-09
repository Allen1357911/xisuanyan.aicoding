package org.example.dormrepairsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.mapper.DormitoryMapper;
import org.example.dormrepairsystem.service.DormitoryService;
import org.springframework.stereotype.Service;

/**
 * 宿舍绑定服务实现类
 */
@Service
public class DormitoryServiceImpl extends ServiceImpl<DormitoryMapper, Dormitory> implements DormitoryService {

    // 根据用户ID查询绑定的宿舍
    @Override
    public Dormitory getByUserId(Long userId) {
        LambdaQueryWrapper<Dormitory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Dormitory::getUserId, userId);
        return this.getOne(wrapper);
    }

    // 分页查询宿舍列表
    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<Dormitory> getDormitoryPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<Dormitory> page) {
        return this.page(page);
    }
}
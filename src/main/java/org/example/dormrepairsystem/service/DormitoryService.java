package org.example.dormrepairsystem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.dormrepairsystem.entity.Dormitory;

/**
 * 宿舍绑定服务接口
 */
public interface DormitoryService extends IService<Dormitory> {
    // 根据用户ID查询绑定的宿舍信息（学生专属）
    Dormitory getByUserId(Long userId);
    // 分页查询宿舍列表
    IPage<Dormitory> getDormitoryPage(Page<Dormitory> page);
}
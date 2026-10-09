package org.example.dormrepairsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.entity.OrderImage;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.entity.User;
import org.example.dormrepairsystem.mapper.DormitoryMapper;
import org.example.dormrepairsystem.mapper.OrderImageMapper;
import org.example.dormrepairsystem.mapper.RepairOrderMapper;
import org.example.dormrepairsystem.mapper.UserMapper;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.service.UserService;
import org.example.dormrepairsystem.util.FileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private DormitoryService dormitoryService;

    @Autowired
    private DormitoryMapper dormitoryMapper;

    @Autowired
    private RepairOrderMapper repairOrderMapper;

    @Autowired
    private OrderImageMapper orderImageMapper;

    @Autowired
    private FileStorage fileStorage;

    // 根据账号查询用户
    @Override
    public User getByAccount(String account) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getAccount, account);
        return this.getOne(wrapper);
    }

    // 注册用户
    @Override
    public boolean register(User user) {
        // 检查账号是否已存在
        if (getByAccount(user.getAccount()) != null) {
            return false;
        }
        
        // 验证账号格式
        String account = user.getAccount();
        
        // 不允许使用admin作为账号
        if ("admin".equals(account)) {
            return false;
        }
        
        // 根据账号设置角色
        if (account.startsWith("3")) {
            // 3开头的账号是学生
            user.setRoleId(1);
        } else if (account.startsWith("2")) {
            // 2开头的账号是维修人员
            user.setRoleId(3);
        } else {
            // 其他账号格式错误
            return false;
        }
        
        // 加密密码
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String encryptedPassword = encoder.encode(user.getPassword());
        user.setPassword(encryptedPassword);
        
        // 保存用户
        return this.save(user);
    }
    
    // 删除用户（级联清理宿舍绑定、报修单及其图片，避免留下孤儿数据）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId) {
        // 不允许删除admin账号
        User user = this.getById(userId);
        if (user == null || "admin".equals(user.getAccount())) {
            return false;
        }

        // 1. 该用户作为报修人提交的订单：删除订单图片（本地文件 + 数据库）后删除订单
        List<RepairOrder> orders = repairOrderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getUserId, userId));
        for (RepairOrder order : orders) {
            deleteOrderImages(order.getOrderId());
        }
        if (!orders.isEmpty()) {
            repairOrderMapper.delete(new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getUserId, userId));
        }

        // 2. 该用户作为维修人员接取的订单：释放回待处理，避免订单卡在维修中
        repairOrderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getRepairmanId, userId)
                .set(RepairOrder::getRepairmanId, null)
                .set(RepairOrder::getOrderStatus, "待处理")
                .set(RepairOrder::getUpdateTime, LocalDateTime.now()));

        // 3. 解除宿舍绑定
        dormitoryMapper.delete(new LambdaQueryWrapper<Dormitory>().eq(Dormitory::getUserId, userId));

        return this.removeById(userId);
    }

    // 删除某个订单的全部图片（先删本地文件，再删数据库记录）
    private void deleteOrderImages(Long orderId) {
        List<OrderImage> images = orderImageMapper.selectList(
                new LambdaQueryWrapper<OrderImage>().eq(OrderImage::getOrderId, orderId));
        for (OrderImage image : images) {
            try {
                fileStorage.deleteImage(image.getImageUrl());
            } catch (Exception e) {
                log.warn("删除订单 {} 的本地图片失败：{}", orderId, e.getMessage());
            }
        }
        if (!images.isEmpty()) {
            orderImageMapper.delete(new LambdaQueryWrapper<OrderImage>().eq(OrderImage::getOrderId, orderId));
        }
    }

    // 将用户列表转换为包含宿舍信息的Map列表
    private List<Map<String, Object>> convertUsersToMapList(List<User> users) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("userId", user.getUserId());
            userMap.put("account", user.getAccount());
            userMap.put("userName", user.getUserName());
            userMap.put("roleId", user.getRoleId());
            userMap.put("createTime", user.getCreateTime());
            userMap.put("updateTime", user.getUpdateTime());
            
            // 只有学生角色才查询宿舍信息
            if (user.getRoleId() == 1) {
                Dormitory dormitory = dormitoryService.getByUserId(user.getUserId());
                if (dormitory != null) {
                    userMap.put("building", dormitory.getBuilding());
                    userMap.put("roomNum", dormitory.getRoomNum());
                }
            }
            
            result.add(userMap);
        }
        return result;
    }

    // 分页查询用户列表（包含宿舍信息）
    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<java.lang.String, java.lang.Object>> getUsersWithDormitoryPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<java.util.Map<java.lang.String, java.lang.Object>> page, 
            Integer roleId) {
        // 构建查询条件
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (roleId != null) {
            wrapper.eq(User::getRoleId, roleId);
        }
        
        // 创建User类型的分页对象
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> userPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page.getCurrent(), page.getSize());
        
        // 执行分页查询
        com.baomidou.mybatisplus.core.metadata.IPage<User> userPageResult = this.page(userPage, wrapper);
        
        // 转换为包含宿舍信息的Map列表
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> userMapList = convertUsersToMapList(userPageResult.getRecords());
        
        // 构建返回的分页结果
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<java.util.Map<java.lang.String, java.lang.Object>> resultPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        resultPage.setRecords(userMapList);
        resultPage.setTotal(userPageResult.getTotal());
        resultPage.setCurrent(userPageResult.getCurrent());
        resultPage.setSize(userPageResult.getSize());
        resultPage.setPages(userPageResult.getPages());
        
        return resultPage;
    }
}
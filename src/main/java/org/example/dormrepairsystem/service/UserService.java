package org.example.dormrepairsystem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.dormrepairsystem.entity.User;

import java.util.Map;

/**
 * 用户核心服务接口
 */
public interface UserService extends IService<User> {
    // 根据账号查询用户
    User getByAccount(String account);
    // 注册用户
    boolean register(User user);
    // 删除用户
    boolean deleteUser(Long userId);
    // 分页查询用户列表（包含宿舍信息）
    IPage<Map<String, Object>> getUsersWithDormitoryPage(Page<Map<String, Object>> page, Integer roleId);
}
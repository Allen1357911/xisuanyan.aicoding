package org.example.dormrepairsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.dormrepairsystem.entity.Role;

/**
 * 角色服务接口
 */
public interface RoleService extends IService<Role> {
    // 根据角色编码查询角色（如student/repairman/admin）
    Role getByRoleCode(String roleCode);
}

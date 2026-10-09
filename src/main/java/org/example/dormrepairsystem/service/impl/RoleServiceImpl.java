package org.example.dormrepairsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.dormrepairsystem.entity.Role;
import org.example.dormrepairsystem.mapper.RoleMapper;
import org.example.dormrepairsystem.service.RoleService;
import org.springframework.stereotype.Service;

/**
 * 角色服务实现类
 */
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    // 根据角色编码查询角色
    @Override
    public Role getByRoleCode(String roleCode) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, roleCode);
        return this.getOne(wrapper);
    }
}
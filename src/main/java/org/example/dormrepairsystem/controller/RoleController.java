package org.example.dormrepairsystem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.dormrepairsystem.entity.Role;
import org.example.dormrepairsystem.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/role")
@Tag(name = "角色管理", description = "角色相关接口")
public class RoleController {

    @Autowired
    private RoleService roleService;

    // 查询所有角色
    @GetMapping("/list")
    @Operation(summary = "查询所有角色", description = "获取系统中所有角色列表")
    public List<Role> list() {
        return roleService.list();
    }

    // 根据角色编码查询角色
    @GetMapping("/code/{roleCode}")
    @Operation(summary = "根据角色编码查询角色", description = "根据角色编码获取角色信息")
    public Role getByRoleCode(
            @Parameter(description = "角色编码", required = true) @PathVariable String roleCode) {
        return roleService.getByRoleCode(roleCode);
    }
}
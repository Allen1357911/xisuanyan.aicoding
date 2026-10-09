package org.example.dormrepairsystem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.dormrepairsystem.entity.User;
import org.example.dormrepairsystem.service.UserService;
import org.example.dormrepairsystem.util.AuthUtil;
import org.example.dormrepairsystem.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/users")
@Slf4j
@Tag(name = "用户管理", description = "用户相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录并获取令牌")
    public ResponseEntity<Map<String, Object>> login(
            @Parameter(description = "登录信息，包含账号和密码", required = true) @RequestBody User user) {
        Map<String, Object> result = new HashMap<>();

        if (user.getAccount() == null || user.getAccount().isBlank()
                || user.getPassword() == null || user.getPassword().isEmpty()) {
            result.put("success", false);
            result.put("message", "账号和密码不能为空");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }

        log.info("用户登录：账号={}", user.getAccount());
        User existingUser = userService.getByAccount(user.getAccount());
        if (existingUser != null && passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
            // 生成JWT令牌
            Map<String, Object> claims = jwtUtil.buildClaims(
                    existingUser.getUserId(), existingUser.getRoleId(), existingUser.getUserName());
            String accessToken = jwtUtil.generateAccessToken(claims);
            String refreshToken = jwtUtil.generateRefreshToken(claims);

            log.info("登录成功：用户ID={}, 角色ID={}", existingUser.getUserId(), existingUser.getRoleId());
            result.put("success", true);
            result.put("message", "登录成功");
            // User.password 标注了 WRITE_ONLY，序列化时不会把密码哈希返回给前端
            result.put("user", existingUser);
            result.put("accessToken", accessToken);
            result.put("refreshToken", refreshToken);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }

        log.warn("登录失败：账号或密码错误，账号={}", user.getAccount());
        result.put("success", false);
        result.put("message", "账号或密码错误");
        return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 用户注册
     */
    @PostMapping
    @Operation(summary = "用户注册", description = "用户注册新账号")
    public ResponseEntity<Map<String, Object>> register(
            @Parameter(description = "注册信息，包含账号、密码和姓名", required = true) @RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        log.info("用户注册：账号={}, 姓名={}", user.getAccount(), user.getUserName());

        String account = user.getAccount();
        if (account == null || account.isBlank()) {
            result.put("success", false);
            result.put("message", "账号不能为空");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            result.put("success", false);
            result.put("message", "密码不能为空");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
        if (user.getUserName() == null || user.getUserName().isBlank()) {
            result.put("success", false);
            result.put("message", "姓名不能为空");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }

        // 验证账号格式
        account = account.trim();
        user.setAccount(account);

        if ("admin".equals(account)) {
            log.warn("注册失败：不允许使用admin作为账号");
            result.put("success", false);
            result.put("message", "账号格式错误，不允许使用admin作为账号");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
        if (!account.startsWith("2") && !account.startsWith("3")) {
            log.warn("注册失败：账号格式错误，只能以2或3开头，账号={}", account);
            result.put("success", false);
            result.put("message", "账号格式错误，只能以2或3开头");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }

        boolean success = userService.register(user);
        if (success) {
            log.info("注册成功：账号={}", user.getAccount());
            result.put("success", true);
            result.put("message", "注册成功");
            return new ResponseEntity<>(result, HttpStatus.CREATED);
        } else {
            log.warn("注册失败：账号已存在，账号={}", user.getAccount());
            result.put("success", false);
            result.put("message", "账号已存在");
            return new ResponseEntity<>(result, HttpStatus.CONFLICT);
        }
    }

    /**
     * 根据角色ID查询用户列表（仅管理员）
     */
    @GetMapping
    @Operation(summary = "查询用户列表", description = "根据角色ID查询用户列表，支持分页")
    public ResponseEntity<Map<String, Object>> listUsers(
            @Parameter(description = "角色ID，1-学生，2-管理员，3-维修人员") @RequestParam(required = false) Integer roleId,
            @Parameter(description = "页码，从1开始") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(required = false, defaultValue = "5") Integer size,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        if (!AuthUtil.isAdmin(request)) {
            result.put("success", false);
            result.put("message", "只有管理员可以查看用户列表");
            return new ResponseEntity<>(result, HttpStatus.FORBIDDEN);
        }

        log.info("查询用户列表：角色ID={}, 页码={}, 每页大小={}", roleId, page, size);

        // 使用MyBatis-Plus的分页功能
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> pageObj = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        com.baomidou.mybatisplus.core.metadata.IPage<Map<String, Object>> pageResult = userService.getUsersWithDormitoryPage(pageObj, roleId);

        log.info("查询用户列表成功：角色ID={}, 总数量={}, 总页数={}", roleId, pageResult.getTotal(), pageResult.getPages());
        result.put("success", true);
        result.put("data", pageResult);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * 删除用户（仅管理员，同时清理其宿舍绑定、报修单和图片）
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "删除用户", description = "根据用户ID删除用户")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @Parameter(description = "用户ID", required = true) @PathVariable Long userId,
            HttpServletRequest request) {
        log.info("删除用户：用户ID={}", userId);
        Map<String, Object> result = new HashMap<>();

        if (!AuthUtil.isAdmin(request)) {
            result.put("success", false);
            result.put("message", "只有管理员可以删除用户");
            return new ResponseEntity<>(result, HttpStatus.FORBIDDEN);
        }
        if (Objects.equals(userId, AuthUtil.currentUserId(request))) {
            result.put("success", false);
            result.put("message", "不能删除当前登录的账号");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }

        boolean success = userService.deleteUser(userId);
        if (success) {
            log.info("删除用户成功：用户ID={}", userId);
            result.put("success", true);
            result.put("message", "删除成功");
            return new ResponseEntity<>(result, HttpStatus.OK);
        } else {
            log.warn("删除用户失败：用户ID={}", userId);
            result.put("success", false);
            result.put("message", "删除失败，可能是admin账号或用户不存在");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 刷新访问令牌
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "刷新访问令牌", description = "使用刷新令牌获取新的访问令牌")
    public ResponseEntity<Map<String, Object>> refreshToken(
            @Parameter(description = "刷新令牌请求，包含refreshToken字段", required = true) @RequestBody Map<String, String> refreshTokenRequest) {
        log.info("刷新访问令牌");
        Map<String, Object> result = new HashMap<>();

        // 获取刷新令牌
        String refreshToken = refreshTokenRequest.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("刷新令牌不存在");
            result.put("success", false);
            result.put("message", "刷新令牌不存在");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }

        try {
            // 使用刷新令牌获取新的访问令牌
            String newAccessToken = jwtUtil.refreshAccessToken(refreshToken);
            log.info("访问令牌刷新成功");
            result.put("success", true);
            result.put("message", "访问令牌刷新成功");
            result.put("accessToken", newAccessToken);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.warn("访问令牌刷新失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "刷新令牌无效或已过期");
            return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
        }
    }
}

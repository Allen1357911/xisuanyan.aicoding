package org.example.dormrepairsystem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/dormitories")
@Slf4j
@Tag(name = "宿舍管理", description = "宿舍相关接口")
public class DormitoryController {

    /** 页码默认值：防止 page=0、page=-7 这类请求把非法页码传进查询层 */
    private static final int DEFAULT_PAGE = 1;

    /** 每页默认条数与上限：与 RepairOrderController 共用 application.yml 的 paging.* 配置 */
    @Value("${paging.default-size:5}")
    private int defaultPageSize;

    @Value("${paging.max-size:500}")
    private int maxPageSize;

    @Autowired
    private DormitoryService dormitoryService;

    /**
     * 绑定宿舍
     *
     * 学生只能给自己绑定宿舍（用户ID取令牌，不信任请求体），管理员可以代学生绑定。
     */
    @PostMapping
    @Operation(summary = "绑定宿舍", description = "为用户绑定或更新宿舍信息")
    public ResponseEntity<Map<String, Object>> bindDormitory(
            @Parameter(description = "宿舍信息，包含用户ID、楼栋和房间号", required = true) @RequestBody Dormitory dormitory,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        Long currentUserId = AuthUtil.currentUserId(request);
        if (AuthUtil.isStudent(request)) {
            if (dormitory.getUserId() != null && !Objects.equals(dormitory.getUserId(), currentUserId)) {
                return forbidden(result, "只能绑定自己的宿舍");
            }
            dormitory.setUserId(currentUserId);
        } else if (!AuthUtil.isAdmin(request)) {
            return forbidden(result, "无权绑定宿舍");
        } else if (dormitory.getUserId() == null) {
            return badRequest(result, "用户ID不能为空");
        }

        if (dormitory.getBuilding() == null || dormitory.getBuilding().isBlank()
                || dormitory.getRoomNum() == null || dormitory.getRoomNum().isBlank()) {
            return badRequest(result, "楼栋和房间号不能为空");
        }

        log.info("绑定宿舍：用户ID={}, 楼栋={}, 房间号={}", dormitory.getUserId(), dormitory.getBuilding(), dormitory.getRoomNum());
        try {
            // 先查询用户是否已绑定宿舍
            Dormitory existingDorm = dormitoryService.getByUserId(dormitory.getUserId());
            if (existingDorm != null) {
                // 如果已绑定，更新宿舍信息
                existingDorm.setBuilding(dormitory.getBuilding());
                existingDorm.setRoomNum(dormitory.getRoomNum());
                existingDorm.setUpdateTime(LocalDateTime.now());
                boolean success = dormitoryService.updateById(existingDorm);
                if (success) {
                    log.info("宿舍信息更新成功：用户ID={}", dormitory.getUserId());
                    result.put("success", true);
                    result.put("message", "宿舍信息更新成功");
                    result.put("data", existingDorm);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    log.warn("宿舍信息更新失败：用户ID={}", dormitory.getUserId());
                    result.put("success", false);
                    result.put("message", "宿舍信息更新失败");
                    return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                // 如果未绑定，新增宿舍信息
                dormitory.setCreateTime(LocalDateTime.now());
                dormitory.setUpdateTime(LocalDateTime.now());
                boolean success = dormitoryService.save(dormitory);
                if (success) {
                    log.info("宿舍绑定成功：用户ID={}", dormitory.getUserId());
                    result.put("success", true);
                    result.put("message", "宿舍绑定成功");
                    result.put("data", dormitory);
                    return new ResponseEntity<>(result, HttpStatus.CREATED);
                } else {
                    log.warn("宿舍绑定失败：用户ID={}", dormitory.getUserId());
                    result.put("success", false);
                    result.put("message", "宿舍绑定失败");
                    return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        } catch (Exception e) {
            log.error("操作失败：", e);
            result.put("success", false);
            result.put("message", "操作失败，请稍后重试");
            return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 查询宿舍
     *
     * 学生只能查自己的宿舍，管理员可以查询全部宿舍（分页）。
     */
    @GetMapping
    @Operation(summary = "查询宿舍", description = "根据用户ID查询宿舍信息，或获取所有宿舍（支持分页）")
    public ResponseEntity<Map<String, Object>> listDormitories(
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "页码，从1开始") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(required = false, defaultValue = "5") Integer size,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        page = normalizePage(page);
        size = normalizePageSize(size);

        if (AuthUtil.isStudent(request)) {
            Long currentUserId = AuthUtil.currentUserId(request);
            if (userId != null && !Objects.equals(userId, currentUserId)) {
                return forbidden(result, "只能查看自己的宿舍信息");
            }
            userId = currentUserId;
        } else if (!AuthUtil.isAdmin(request)) {
            return forbidden(result, "无权查看宿舍信息");
        }

        log.info("查询宿舍：用户ID={}, 页码={}, 每页大小={}", userId, page, size);

        if (userId != null) {
            // 根据用户ID查询
            Dormitory dormitory = dormitoryService.getByUserId(userId);
            log.info("查询成功：用户ID={}, 宿舍信息={}", userId, dormitory);
            result.put("success", true);
            result.put("data", dormitory);
        } else {
            // 使用MyBatis-Plus的分页功能
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Dormitory> pageObj = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
            com.baomidou.mybatisplus.core.metadata.IPage<Dormitory> pageResult = dormitoryService.getDormitoryPage(pageObj);

            log.info("查询成功：宿舍总数量={}, 总页数={}", pageResult.getTotal(), pageResult.getPages());
            result.put("success", true);
            result.put("data", pageResult);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * 删除宿舍（仅管理员）
     */
    @DeleteMapping("/{dormId}")
    @Operation(summary = "删除宿舍", description = "根据宿舍ID删除宿舍信息")
    public ResponseEntity<Map<String, Object>> deleteDormitory(
            @Parameter(description = "宿舍ID", required = true) @PathVariable Long dormId,
            HttpServletRequest request) {
        log.info("删除宿舍：宿舍ID={}", dormId);
        Map<String, Object> result = new HashMap<>();

        if (!AuthUtil.isAdmin(request)) {
            return forbidden(result, "只有管理员可以删除宿舍信息");
        }

        boolean success = dormitoryService.removeById(dormId);
        if (success) {
            log.info("删除成功：宿舍ID={}", dormId);
            result.put("success", true);
            result.put("message", "删除成功");
            return new ResponseEntity<>(result, HttpStatus.OK);
        } else {
            log.warn("删除失败：宿舍ID={}", dormId);
            result.put("success", false);
            result.put("message", "删除失败");
            return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(Map<String, Object> result, String message) {
        result.put("success", false);
        result.put("message", message);
        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
    }

    /**
     * 页码兜底：小于 1 或为空时回到第 1 页
     */
    private int normalizePage(Integer page) {
        return (page == null || page < DEFAULT_PAGE) ? DEFAULT_PAGE : page;
    }

    /**
     * 每页条数兜底：为空或小于 1 时用默认值，超过上限时夹到上限
     */
    private int normalizePageSize(Integer size) {
        if (size == null || size < 1) {
            return defaultPageSize;
        }
        return Math.min(size, maxPageSize);
    }

    private ResponseEntity<Map<String, Object>> forbidden(Map<String, Object> result, String message) {
        result.put("success", false);
        result.put("message", message);
        return new ResponseEntity<>(result, HttpStatus.FORBIDDEN);
    }
}

package org.example.dormrepairsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.dormrepairsystem.dto.DeviceTypeStatDTO;
import org.example.dormrepairsystem.dto.OrderWithUserDTO;
import org.example.dormrepairsystem.entity.OrderImage;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.entity.User;
import org.example.dormrepairsystem.mapper.RepairOrderMapper;
import org.example.dormrepairsystem.service.OrderImageService;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.service.UserService;
import org.example.dormrepairsystem.util.FileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 报修单核心服务实现类
 */
@Service
@Slf4j
public class RepairOrderServiceImpl extends ServiceImpl<RepairOrderMapper, RepairOrder> implements RepairOrderService {

    @Autowired
    private UserService userService;

    @Autowired
    private OrderImageService orderImageService;

    @Autowired
    private FileStorage fileStorage;

    // 根据用户ID查询个人报修记录
    @Override
    public List<RepairOrder> getByUserId(Long userId, String status) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RepairOrder::getUserId, userId);
        applyStatusFilter(wrapper, status);
        wrapper.orderByDesc(RepairOrder::getCreateTime); // 按创建时间倒序
        return this.list(wrapper);
    }

    // 根据状态查询报修单列表
    @Override
    public List<RepairOrder> getByOrderStatus(String status) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        applyStatusFilter(wrapper, status);
        wrapper.orderByDesc(RepairOrder::getCreateTime);
        return this.list(wrapper);
    }

    // 分页查询（支持状态筛选，status为null则查全部）
    @Override
    public IPage<RepairOrder> getPage(Page<RepairOrder> page, String status) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        applyStatusFilter(wrapper, status);
        wrapper.orderByDesc(RepairOrder::getUpdateTime); // 按最后修改时间倒序
        return this.page(page, wrapper);
    }

    // 接取订单
    //
    // 必须是原子操作：不能"先 getById 判断状态、再 updateById"，那样多个维修人员并发接单时
    // 会同时通过状态判断、各自把自己写成接单人（实测 8 个并发线程会全部返回成功）。
    // 这里把状态判断合并进 UPDATE 的 WHERE 条件，由数据库保证只有一个线程能更新到 1 行。
    @Override
    public boolean acceptOrder(Long orderId, Long repairmanId) {
        LambdaUpdateWrapper<RepairOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RepairOrder::getOrderId, orderId)
                .eq(RepairOrder::getOrderStatus, "待处理")
                .set(RepairOrder::getRepairmanId, repairmanId)
                .set(RepairOrder::getOrderStatus, "维修中")
                .set(RepairOrder::getUpdateTime, java.time.LocalDateTime.now());

        boolean accepted = this.update(null, wrapper);
        if (!accepted) {
            log.info("接单未生效（订单不存在或已被他人接取）：订单ID={}, 维修人员ID={}", orderId, repairmanId);
        }
        return accepted;
    }

    // 根据维修人员ID查询订单
    @Override
    public List<RepairOrder> getByRepairmanId(Long repairmanId, String status) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        // 缺了这一条，维修人员维度等于没过滤，会把全表工单都返回
        wrapper.eq(RepairOrder::getRepairmanId, repairmanId);
        applyStatusFilter(wrapper, status);
        wrapper.orderByDesc(RepairOrder::getUpdateTime); // 按最后修改时间倒序
        return this.list(wrapper);
    }

    // 分页查询订单（包含用户信息，管理员专属）
    @Override
    public IPage<OrderWithUserDTO> getPageWithUserInfo(Page<RepairOrder> page, String status) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        applyStatusFilter(wrapper, status);
        wrapper.orderByDesc(RepairOrder::getCreateTime);

        // 先分页查出订单，再把当页记录转换成带用户信息的DTO
        IPage<RepairOrder> orderPage = this.page(page, wrapper);

        Page<OrderWithUserDTO> dtoPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderWithUserDTO> dtos = new ArrayList<>();
        for (RepairOrder order : orderPage.getRecords()) {
            dtos.add(toOrderWithUserDTO(order));
        }
        dtoPage.setRecords(dtos);

        return dtoPage;
    }

    // 删除订单并清理其图片（本地文件 + 数据库记录），避免留下孤儿数据
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteOrderWithImages(Long orderId) {
        List<OrderImage> images = orderImageService.getByOrderId(orderId);
        for (OrderImage image : images) {
            try {
                fileStorage.deleteImage(image.getImageUrl());
            } catch (Exception e) {
                // 文件删除失败不影响数据库清理，避免图片删不掉导致订单也删不掉
                log.warn("删除订单 {} 的本地图片失败：{}", orderId, e.getMessage());
            }
        }
        orderImageService.deleteByOrderId(orderId);
        return this.removeById(orderId);
    }

    /**
     * 按设备类型统计报修次数（管理员专属）
     *
     * 统计口径是全部工单，不按状态过滤，已取消的工单同样计入；按数量从大到小返回。
     */
    @Override
    public List<DeviceTypeStatDTO> countByDeviceType() {
        QueryWrapper<RepairOrder> wrapper = new QueryWrapper<>();
        wrapper.select("device_type AS deviceType", "COUNT(*) AS count")
                .isNotNull("device_type")
                .groupBy("device_type");

        List<Map<String, Object>> rows = this.listMaps(wrapper);

        List<DeviceTypeStatDTO> stats = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DeviceTypeStatDTO dto = new DeviceTypeStatDTO();
            // 返回 Map 时列名可能按小写下发（device_type -> devicetype），两种键名都要兼容
            Object deviceType = row.get("deviceType");
            if (deviceType == null) {
                deviceType = row.get("devicetype");
            }
            dto.setDeviceType(deviceType == null ? null : deviceType.toString());

            Object count = row.get("count");
            if (count == null) {
                count = row.get("COUNT(*)");
            }
            dto.setCount(count == null ? 0L : ((Number) count).longValue());
            stats.add(dto);
        }

        // 排序放在内存里做，避免依赖数据库对别名的支持程度
        stats.sort(Comparator.comparingLong(DeviceTypeStatDTO::getCount).reversed());
        return stats;
    }

    /**
     * 状态筛选：支持逗号分隔的多个状态，例如 "已完成,已取消"
     */
    private void applyStatusFilter(LambdaQueryWrapper<RepairOrder> wrapper, String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        List<String> statuses = Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (statuses.size() == 1) {
            wrapper.eq(RepairOrder::getOrderStatus, statuses.get(0));
        } else if (statuses.size() > 1) {
            wrapper.in(RepairOrder::getOrderStatus, statuses);
        }
    }

    // 将订单实体转换为包含用户信息的DTO
    private OrderWithUserDTO toOrderWithUserDTO(RepairOrder order) {
        OrderWithUserDTO dto = new OrderWithUserDTO();
        // 复制订单基本信息
        dto.setOrderId(order.getOrderId());
        dto.setUserId(order.getUserId());
        dto.setDormId(order.getDormId());
        dto.setBuilding(order.getBuilding());
        dto.setRoomNum(order.getRoomNum());
        dto.setRepairmanId(order.getRepairmanId());
        dto.setDeviceType(order.getDeviceType());
        dto.setProblemDesc(order.getProblemDesc());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());

        // 查询学生信息
        if (order.getUserId() != null) {
            User student = userService.getById(order.getUserId());
            if (student != null) {
                dto.setUserName(student.getUserName());
            }
        }

        // 查询维修人员信息
        if (order.getRepairmanId() != null) {
            User repairman = userService.getById(order.getRepairmanId());
            if (repairman != null) {
                dto.setRepairmanName(repairman.getUserName());
            }
        }

        return dto;
    }
}

package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dormrepairsystem.dto.DeviceTypeStatDTO;
import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.entity.OrderImage;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.entity.User;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.service.OrderImageService;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.service.UserService;
import org.example.dormrepairsystem.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备类型统计在真实数据库（内嵌 H2 + 种子数据）上的口径验证
 *
 * 这里回答的是光看代码和 mock 都答不了的问题：SQL 分组是否真的生效、
 * 已取消的工单是否被计入、返回的字段名是否与接口契约一致、排序是否从大到小。
 * 每个用例在事务里执行并回滚，不会污染其它测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeviceTypeStatsIntegrationTest {

    /** 故意用种子数据里没有的设备类型，断言时不受种子工单数量影响 */
    private static final String DEVICE_TYPE_A = "统计测试设备甲";
    private static final String DEVICE_TYPE_B = "统计测试设备乙";

    /** 与 application.yml 的 file.storage.location 保持一致；测试文件用完即删，且 uploads/ 已被 .gitignore 挡住 */
    private static final String UPLOAD_LOCATION = "uploads";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private DormitoryService dormitoryService;

    @Autowired
    private RepairOrderService repairOrderService;

    @Autowired
    private OrderImageService orderImageService;

    private RepairOrder newOrder(String deviceType, String status) {
        User student = userService.getByAccount("3001");
        assertNotNull(student, "种子学生 3001 应存在");
        Dormitory dormitory = dormitoryService.getByUserId(student.getUserId());
        assertNotNull(dormitory, "种子学生应已绑定宿舍");

        RepairOrder order = new RepairOrder();
        order.setUserId(student.getUserId());
        order.setDormId(dormitory.getDormId());
        order.setBuilding(dormitory.getBuilding());
        order.setRoomNum(dormitory.getRoomNum());
        order.setDeviceType(deviceType);
        order.setProblemDesc("统计口径测试");
        order.setOrderStatus(status);
        assertTrue(repairOrderService.save(order), "工单应保存成功");
        assertNotNull(order.getOrderId(), "自增主键应回填");
        return order;
    }

    private DeviceTypeStatDTO pick(List<DeviceTypeStatDTO> stats, String deviceType) {
        return stats.stream().filter(s -> deviceType.equals(s.getDeviceType())).findFirst().orElse(null);
    }

    private long indexOf(List<DeviceTypeStatDTO> stats, String deviceType) {
        for (int i = 0; i < stats.size(); i++) {
            if (deviceType.equals(stats.get(i).getDeviceType())) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void countsGroupByDeviceTypeAndIncludeCancelledOrders() {
        // 甲：2 条，其中 1 条已取消（必须计入）；乙：1 条
        newOrder(DEVICE_TYPE_A, "待处理");
        newOrder(DEVICE_TYPE_A, "已取消");
        newOrder(DEVICE_TYPE_B, "维修中");

        List<DeviceTypeStatDTO> stats = repairOrderService.countByDeviceType();

        DeviceTypeStatDTO statA = pick(stats, DEVICE_TYPE_A);
        DeviceTypeStatDTO statB = pick(stats, DEVICE_TYPE_B);
        assertNotNull(statA, "统计结果里应包含设备甲");
        assertNotNull(statB, "统计结果里应包含设备乙");
        assertEquals(2L, statA.getCount(), "已取消的工单也要计入该设备类型的数量");
        assertEquals(1L, statB.getCount());
    }

    @Test
    void statsAreSortedByCountDescending() {
        // 乙 2 条、甲 1 条：数量相同时顺序不限，但甲必须排在乙之后
        newOrder(DEVICE_TYPE_B, "待处理");
        newOrder(DEVICE_TYPE_B, "已完成");
        newOrder(DEVICE_TYPE_A, "待处理");

        List<DeviceTypeStatDTO> stats = repairOrderService.countByDeviceType();

        long indexA = indexOf(stats, DEVICE_TYPE_A);
        long indexB = indexOf(stats, DEVICE_TYPE_B);
        assertEquals(2L, pick(stats, DEVICE_TYPE_B).getCount());
        assertEquals(1L, pick(stats, DEVICE_TYPE_A).getCount());
        assertTrue(indexB >= 0 && indexA >= 0, "两个设备类型都应出现在统计结果里");
        assertTrue(indexB < indexA, "数量多的设备类型必须排在前面，实际顺序：" + indexB + " vs " + indexA);
    }

    @Test
    void everyRowCarriesDeviceTypeAndNumericCount() {
        newOrder(DEVICE_TYPE_A, "待处理");

        List<DeviceTypeStatDTO> stats = repairOrderService.countByDeviceType();

        assertTrue(stats.size() >= 5, "4 个种子设备类型 + 本次新增的甲，实际：" + stats.size());
        for (DeviceTypeStatDTO stat : stats) {
            assertNotNull(stat.getDeviceType(), "deviceType 不能为 null（列名映射错了就会这样）");
            assertTrue(stat.getCount() != null && stat.getCount() >= 1, "count 必须是正整数");
        }
    }

    @Test
    void endpointReturnsStatsInContractFormat() throws Exception {
        newOrder(DEVICE_TYPE_A, "已取消");
        newOrder(DEVICE_TYPE_A, "待处理");

        String adminToken = "Bearer " + jwtUtil.generateAccessToken(jwtUtil.buildClaims(1L, 2, "系统管理员"));

        mockMvc.perform(get("/repair-orders/stats/device-type").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---------- T1 的维修人员维度过滤：这是那个 bug 的正规防线 ----------

    @Test
    void repairmanQueryReturnsOnlyOrdersAssignedToThatRepairman() {
        // 种子数据：工单 2、3 属于维修人员 4；工单 1、4 无人接单
        List<RepairOrder> before = repairOrderService.getByRepairmanId(4L, null);
        assertEquals(2, before.size(), "种子数据里维修人员 4 应有 2 条工单");

        RepairOrder mine = newOrderForRepairman(4L, "维修中");
        RepairOrder other = newOrderForRepairman(5L, "维修中");
        RepairOrder nobody = newOrderForRepairman(null, "待处理");

        List<RepairOrder> result = repairOrderService.getByRepairmanId(4L, null);

        List<Long> ids = result.stream().map(RepairOrder::getOrderId).toList();
        assertTrue(ids.contains(mine.getOrderId()), "本人接取的工单必须在结果里");
        assertFalse(ids.contains(other.getOrderId()), "别人接取的工单不能出现在结果里");
        assertFalse(ids.contains(nobody.getOrderId()), "没人接的工单不能出现在结果里");
        for (RepairOrder order : result) {
            assertEquals(4L, order.getRepairmanId(), "返回的每一条都必须属于维修人员 4");
        }
    }

    @Test
    void repairmanQueryWithUnknownIdReturnsEmptyList() {
        newOrderForRepairman(4L, "维修中");

        List<RepairOrder> result = repairOrderService.getByRepairmanId(999999L, null);

        assertTrue(result.isEmpty(), "不存在的维修人员应当返回空列表，而不是全表");
    }

    @Test
    void repairmanQueryStillAppliesStatusFilter() {
        RepairOrder mine = newOrderForRepairman(4L, "待处理");
        // 另一个维修人员名下有同状态的工单：只有"既按人过滤、又按状态过滤"才能把它排除掉
        RepairOrder other = newOrderForRepairman(5L, "待处理");

        List<RepairOrder> pending = repairOrderService.getByRepairmanId(4L, "待处理");

        List<Long> ids = pending.stream().map(RepairOrder::getOrderId).toList();
        assertTrue(ids.contains(mine.getOrderId()), "自己的待处理工单必须在结果里");
        assertFalse(ids.contains(other.getOrderId()), "别人名下的同状态工单必须被排除");
        for (RepairOrder order : pending) {
            assertEquals(4L, order.getRepairmanId());
            assertEquals("待处理", order.getOrderStatus());
        }
        assertTrue(repairOrderService.getByRepairmanId(4L, "待确认").isEmpty(),
                "维修人员 4 名下没有待确认的工单");
    }

    /** 建一条指定接单人的工单，直接写库，用于验证服务层的过滤条件 */
    private RepairOrder newOrderForRepairman(Long repairmanId, String status) {
        RepairOrder order = newOrder(DEVICE_TYPE_A, status);
        order.setRepairmanId(repairmanId);
        assertTrue(repairOrderService.updateById(order), "回填接单人应成功");
        return order;
    }

    // ---------- 分页与多状态筛选的边界（真库） ----------

    @Test
    void pageBeyondLastPageReturnsEmptyRecordsButHonestTotal() {
        IPage<RepairOrder> page = repairOrderService.getPage(new Page<>(9999, 10), null);

        assertTrue(page.getRecords().isEmpty(), "超出末页应返回空记录而不是报错");
        assertEquals(4, page.getTotal(), "total 必须仍然是真实总数，不受页码影响");
    }

    @Test
    void commaSeparatedStatusFilterWorksAndTrimsSpaces() {
        List<RepairOrder> merged = repairOrderService.getByOrderStatus("已完成, 已取消");
        assertEquals(2, merged.size(), "种子里已完成与已取消各 1 条");
        for (RepairOrder order : merged) {
            assertTrue("已完成".equals(order.getOrderStatus()) || "已取消".equals(order.getOrderStatus()),
                    "多状态筛选只应返回这两类状态：" + order.getOrderStatus());
        }

        // 结尾多余逗号不应产生空状态条件
        assertEquals(merged.size(), repairOrderService.getByOrderStatus("已完成,已取消,").size());
        // 单一状态仍走 eq
        assertEquals(1, repairOrderService.getByOrderStatus("已完成").size());
        // 未知状态不报错，返回空列表
        assertTrue(repairOrderService.getByOrderStatus("不存在的状态").isEmpty());
    }

    @Test
    void pagedOrderStatusFilterKeepsOrderingAndTotal() {
        IPage<RepairOrder> page = repairOrderService.getPage(new Page<>(1, 2), null);

        assertEquals(4, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertEquals(2L, page.getRecords().get(0).getOrderId(),
                "按 update_time 倒序时最新的一条应是工单 2（与既有 DatabaseIntegrationTest 的口径一致）");
    }

    // ---------- 删订单必须连带删掉磁盘文件（真实 FileStorage，不 mock） ----------

    @Test
    void deletingOrderRemovesItsImageFileFromDisk() throws Exception {
        User student = userService.getByAccount("3001");
        Dormitory dormitory = dormitoryService.getByUserId(student.getUserId());
        assertNotNull(dormitory);

        RepairOrder order = new RepairOrder();
        order.setUserId(student.getUserId());
        order.setDormId(dormitory.getDormId());
        order.setBuilding(dormitory.getBuilding());
        order.setRoomNum(dormitory.getRoomNum());
        order.setDeviceType(DEVICE_TYPE_A);
        order.setProblemDesc("删除连带清理文件测试");
        order.setOrderStatus("待处理");
        assertTrue(repairOrderService.save(order));

        String relative = "repair-orders/cascade-" + order.getOrderId() + ".png";
        Path file = Paths.get(UPLOAD_LOCATION).resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        OrderImage image = new OrderImage();
        image.setOrderId(order.getOrderId());
        image.setImageUrl("/uploads/" + relative);
        assertTrue(orderImageService.saveOrderImage(image));
        assertTrue(Files.exists(file), "前置条件：文件应已落盘");

        assertTrue(repairOrderService.deleteOrderWithImages(order.getOrderId()));

        assertFalse(Files.exists(file), "删除订单必须把磁盘上的图片文件一并删掉，而不只是删数据库记录");
        assertTrue(orderImageService.getByOrderId(order.getOrderId()).isEmpty(), "图片记录也应清理");
    }
}

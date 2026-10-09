package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dormrepairsystem.dto.OrderWithUserDTO;
import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.entity.OrderImage;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.entity.User;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.service.OrderImageService;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 SQL 集成测试
 *
 * 跑在内嵌 H2 + sql/schema.sql + sql/data.sql 上，用来验证那些光看代码看不出、
 * mock 也覆盖不到的东西：
 *   1. 实体字段与数据库列名是否一一对应（列名写错会直接抛 column not found）
 *   2. createTime / updateTime 的自动填充在真实 insert / update 上是否生效
 *   3. 状态筛选（单状态 eq、多状态 in）与分页在真实 SQL 上的行为
 *   4. 删除订单 / 删除用户的级联清理是否真的把关联数据删干净
 *
 * 每个用例都在事务里执行并回滚，互不影响。
 */
@SpringBootTest
@Transactional
class DatabaseIntegrationTest {

    private static final String SEED_STUDENT = "3001";
    private static final String ANOTHER_STUDENT = "3002";
    private static final String SEED_REPAIRMAN = "2001";

    @Autowired
    private UserService userService;

    @Autowired
    private DormitoryService dormitoryService;

    @Autowired
    private RepairOrderService repairOrderService;

    @Autowired
    private OrderImageService orderImageService;

    @Test
    void seedDataIsLoadedAndPasswordsMatch() {
        User admin = userService.getByAccount("admin");
        assertNotNull(admin, "初始化数据里的 admin 账号没有加载");
        assertEquals(2, admin.getRoleId());
        assertTrue(new BCryptPasswordEncoder().matches("admin123", admin.getPassword()));

        User student = userService.getByAccount(SEED_STUDENT);
        assertNotNull(student, "初始化数据里没有学生账号 " + SEED_STUDENT);
        assertEquals(1, student.getRoleId());
        assertTrue(new BCryptPasswordEncoder().matches("123456", student.getPassword()));

        User repairman = userService.getByAccount(SEED_REPAIRMAN);
        assertNotNull(repairman, "初始化数据里没有维修人员账号 " + SEED_REPAIRMAN);
        assertEquals(3, repairman.getRoleId(),
                "维修人员角色ID必须是3（注册2开头账号会写到role_id=3，角色表里没有会触发外键错误）");
    }

    @Test
    void entityFieldsMatchRealColumns() {
        User student = userService.getByAccount(SEED_STUDENT);
        Dormitory dormitory = dormitoryService.getByUserId(student.getUserId());
        assertNotNull(dormitory, "种子数据里学生应已绑定宿舍");
        assertEquals("1栋", dormitory.getBuilding());
        assertEquals("502", dormitory.getRoomNum());
        assertNotNull(dormitory.getCreateTime());

        List<RepairOrder> orders = repairOrderService.getByUserId(student.getUserId(), null);
        assertFalse(orders.isEmpty(), "种子数据里该学生应有报修单");
        RepairOrder order = orders.get(0);
        assertNotNull(order.getOrderId());
        assertNotNull(order.getDormId());
        assertNotNull(order.getDeviceType());
        assertNotNull(order.getProblemDesc());
        assertNotNull(order.getOrderStatus());
        assertNotNull(order.getCreateTime(), "create_time 读出来是空，说明列名映射有问题");
        assertNotNull(order.getUpdateTime(), "update_time 读出来是空，说明列名映射有问题");
        assertNotNull(order.getBuilding());
        assertNotNull(order.getRoomNum());
    }

    @Test
    void insertAutoFillsTimeFields() {
        User student = userService.getByAccount(SEED_STUDENT);
        Dormitory dormitory = dormitoryService.getByUserId(student.getUserId());

        RepairOrder order = new RepairOrder();
        order.setUserId(student.getUserId());
        order.setDormId(dormitory.getDormId());
        order.setBuilding(dormitory.getBuilding());
        order.setRoomNum(dormitory.getRoomNum());
        order.setDeviceType("插座");
        order.setProblemDesc("床头插座没电");
        order.setOrderStatus("待处理");
        // 故意不设置 createTime / updateTime，交给 MyBatis-Plus 自动填充

        assertTrue(repairOrderService.save(order));
        assertNotNull(order.getOrderId(), "自增主键没有回填到实体");
        assertNotNull(order.getCreateTime(), "create_time 没有被自动填充");
        assertNotNull(order.getUpdateTime(), "update_time 没有被自动填充");

        RepairOrder saved = repairOrderService.getById(order.getOrderId());
        assertNotNull(saved.getCreateTime());
        assertNotNull(saved.getUpdateTime());
    }

    @Test
    void updateAutoRefreshesUpdateTime() {
        User student = userService.getByAccount(SEED_STUDENT);
        RepairOrder order = repairOrderService.getByUserId(student.getUserId(), "待处理").get(0);
        LocalDateTime before = order.getUpdateTime();
        assertNotNull(before);

        RepairOrder update = new RepairOrder();
        update.setOrderId(order.getOrderId());
        update.setOrderStatus("维修中");
        assertTrue(repairOrderService.updateById(update));

        RepairOrder after = repairOrderService.getById(order.getOrderId());
        assertEquals("维修中", after.getOrderStatus());
        assertNotNull(after.getUpdateTime());
        assertTrue(after.getUpdateTime().isAfter(before),
                "更新后 update_time 没有刷新：" + before + " -> " + after.getUpdateTime());
    }

    @Test
    void statusFilterWorksOnRealSql() {
        Long userId = userService.getByAccount(SEED_STUDENT).getUserId();

        assertEquals(2, repairOrderService.getByUserId(userId, null).size(), "该学生共有 2 条种子报修单");
        assertEquals(1, repairOrderService.getByUserId(userId, "待处理").size());
        assertEquals(1, repairOrderService.getByUserId(userId, "维修中").size());
        assertEquals(0, repairOrderService.getByUserId(userId, "已取消").size());

        // 逗号分隔的多状态走 in 查询
        assertEquals(1, repairOrderService.getByUserId(userId, "维修中,已完成").size());
        assertEquals(2, repairOrderService.getByUserId(userId, "待处理, 维修中").size());
        assertEquals(0, repairOrderService.getByUserId(userId, "已完成, 已取消").size());
    }

    @Test
    void paginationWorksOnRealSql() {
        IPage<RepairOrder> page = repairOrderService.getPage(new Page<>(1, 2), null);
        assertEquals(4, page.getTotal(), "种子数据共 4 条报修单");
        assertEquals(2, page.getRecords().size(), "每页 2 条");
        assertEquals(2, page.getPages());
        // 按 update_time 倒序，最新的那条应该是 order_id = 2
        assertEquals(2L, page.getRecords().get(0).getOrderId());

        IPage<OrderWithUserDTO> dtoPage = repairOrderService.getPageWithUserInfo(new Page<>(1, 2), null);
        assertEquals(4, dtoPage.getTotal());
        assertEquals(2, dtoPage.getRecords().size());
        assertNotNull(dtoPage.getRecords().get(0).getUserName(), "DTO 里的学生姓名应该能查出用户信息");

        IPage<OrderWithUserDTO> filtered = repairOrderService.getPageWithUserInfo(new Page<>(1, 10), "已完成,已取消");
        assertEquals(2, filtered.getTotal(), "多状态筛选在分页查询里也要生效");
    }

    @Test
    void deleteOrderAlsoRemovesItsImages() {
        User student = userService.getByAccount(SEED_STUDENT);
        RepairOrder order = repairOrderService.getByUserId(student.getUserId(), "待处理").get(0);

        OrderImage image = new OrderImage();
        image.setOrderId(order.getOrderId());
        image.setImageUrl("/uploads/repair-orders/not-exists-yet.png");
        assertTrue(orderImageService.saveOrderImage(image));
        assertNotNull(image.getId(), "order_image 主键没有回填");
        assertNotNull(image.getCreateTime(), "order_image.create_time 没有被自动填充");
        assertEquals(1, orderImageService.getByOrderId(order.getOrderId()).size());

        assertTrue(repairOrderService.deleteOrderWithImages(order.getOrderId()));

        assertNull(repairOrderService.getById(order.getOrderId()));
        assertTrue(orderImageService.getByOrderId(order.getOrderId()).isEmpty(), "删除订单后图片记录应一并清理");
    }

    @Test
    void deletingUserClearsDormitoryAndOrders() {
        User student = userService.getByAccount(SEED_STUDENT);
        Long userId = student.getUserId();
        assertNotNull(dormitoryService.getByUserId(userId));
        assertFalse(repairOrderService.getByUserId(userId, null).isEmpty());

        assertTrue(userService.deleteUser(userId));

        assertNull(userService.getById(userId), "用户没有被删除");
        assertNull(dormitoryService.getByUserId(userId), "宿舍绑定没有被清理");
        assertTrue(repairOrderService.getByUserId(userId, null).isEmpty(), "报修单没有被清理");
    }

    @Test
    void oneStudentCannotBindTwoDormitories() {
        User student = userService.getByAccount(ANOTHER_STUDENT);
        assertNotNull(dormitoryService.getByUserId(student.getUserId()), "该学生应已绑定宿舍");

        Dormitory another = new Dormitory();
        another.setUserId(student.getUserId());
        another.setBuilding("9栋");
        another.setRoomNum("101");

        assertThrows(DataAccessException.class, () -> dormitoryService.save(another),
                "一个学生重复绑定宿舍应被唯一约束拒绝");
    }
}

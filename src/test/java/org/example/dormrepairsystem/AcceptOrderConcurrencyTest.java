package org.example.dormrepairsystem;

import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.entity.User;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接单并发竞争（TOCTOU）的验证
 *
 * 原实现是「先 getById 判断状态、再 updateById」两步，两个线程可以同时通过判断、
 * 各自把自己写成接单人。这里用真实数据库和真实线程池复现这个窗口，断言：
 *   - 调用方只会收到 1 个 true（其余拿到 false，控制器据此返回 400「已被接取」）
 *   - 数据库最终只落一个接单人
 *
 * 不使用 @Transactional：并发线程需要真正提交到库里才能互相看见，测试自行清理数据。
 */
@SpringBootTest
class AcceptOrderConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private UserService userService;

    @Autowired
    private DormitoryService dormitoryService;

    @Autowired
    private RepairOrderService repairOrderService;

    @Test
    void concurrentAcceptOnlyOneRepairmanWins() throws Exception {
        Long orderId = createPendingOrder();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> contenderIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            contenderIds.add(900L + i);
        }

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Long contenderId : contenderIds) {
            futures.add(pool.submit((Callable<Boolean>) () -> {
                start.await(5, TimeUnit.SECONDS);
                return repairOrderService.acceptOrder(orderId, contenderId);
            }));
        }
        start.countDown();

        int winners = 0;
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get(20, TimeUnit.SECONDS))) {
                winners++;
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        RepairOrder finalOrder = repairOrderService.getById(orderId);
        assertNotNull(finalOrder, "工单不应被删掉");
        try {
            assertEquals(1, winners,
                    "并发接单只允许一个线程成功；若出现多个 true，说明状态判断与更新之间存在竞态窗口");
            assertEquals("维修中", finalOrder.getOrderStatus());
            assertNotNull(finalOrder.getRepairmanId(), "最终必须落一个接单人");
            assertTrue(contenderIds.contains(finalOrder.getRepairmanId()),
                    "接单人必须是参与竞争的线程之一，实际：" + finalOrder.getRepairmanId());
        } finally {
            repairOrderService.removeById(orderId);
        }
    }

    private Long createPendingOrder() {
        User student = userService.getByAccount("3001");
        assertNotNull(student, "种子学生 3001 应存在");
        Dormitory dormitory = dormitoryService.getByUserId(student.getUserId());
        assertNotNull(dormitory);

        RepairOrder order = new RepairOrder();
        order.setUserId(student.getUserId());
        order.setDormId(dormitory.getDormId());
        order.setBuilding(dormitory.getBuilding());
        order.setRoomNum(dormitory.getRoomNum());
        order.setDeviceType("并发测试设备");
        order.setProblemDesc("并发接单竞争验证");
        order.setOrderStatus("待处理");
        assertTrue(repairOrderService.save(order));
        return order.getOrderId();
    }
}

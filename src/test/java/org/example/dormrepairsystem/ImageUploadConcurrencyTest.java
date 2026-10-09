package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 并发上传的验证：每单最多 3 张的限制在并发下也不能被突破
 *
 * 这里用真实数据库 + 真实 FileStorage（不打桩），8 个线程同时对同一张工单上传，
 * 断言"正好 3 个 201、其余 400"，并确认被拒请求不会在磁盘上留下孤儿文件。
 *
 * 修复前的写法是"先查数量、再插入"两步，并发下会一起通过数量校验——同类竞态在
 * acceptOrder 上已实测复现（8/8 成功），本用例是它在图片上传链路上的对应防线。
 * 不使用 @Transactional：并发线程需要真正提交才互相可见，测试自行清理数据与文件。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImageUploadConcurrencyTest {

    private static final int THREADS = 8;
    private static final int LIMIT = 3;
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

    @Test
    void concurrentUploadsCannotExceedImageLimit() throws Exception {
        User student = userService.getByAccount("3001");
        assertNotNull(student);
        Dormitory dormitory = dormitoryService.getByUserId(student.getUserId());
        assertNotNull(dormitory);

        RepairOrder order = new RepairOrder();
        order.setUserId(student.getUserId());
        order.setDormId(dormitory.getDormId());
        order.setBuilding(dormitory.getBuilding());
        order.setRoomNum(dormitory.getRoomNum());
        order.setDeviceType("并发上传测试设备");
        order.setProblemDesc("并发上传不能突破 3 张上限");
        order.setOrderStatus("待处理");
        assertTrue(repairOrderService.save(order));
        Long orderId = order.getOrderId();

        String token = "Bearer " + jwtUtil.generateAccessToken(jwtUtil.buildClaims(student.getUserId(), 1, "张三"));
        byte[] png = pngBytes();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            final int seq = i;
            futures.add(pool.submit((Callable<Integer>) () -> {
                MockMultipartFile file = new MockMultipartFile("file", "c" + seq + ".png", "image/png", png);
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(multipart("/repair-orders/" + orderId + "/images")
                                .file(file).header("Authorization", token))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        long created = statuses.stream().filter(s -> s == 201).count();
        long rejected = statuses.stream().filter(s -> s == 400).count();

        try {
            assertEquals(LIMIT, created,
                    "并发上传后成功数必须正好等于上限，实际状态码：" + statuses);
            assertEquals(THREADS - LIMIT, rejected,
                    "其余请求必须被拒（400），实际状态码：" + statuses);
            assertEquals(LIMIT, orderImageService.getByOrderId(orderId).size(),
                    "数据库里的图片记录数必须等于上限");
            assertEquals(LIMIT, countFilesOnDisk(orderId),
                    "磁盘上的文件数也必须等于上限，被拒的请求不能留下孤儿文件");
        } finally {
            cleanup(orderId);
        }
    }

    private int countFilesOnDisk(Long orderId) throws Exception {
        List<OrderImage> images = orderImageService.getByOrderId(orderId);
        int existing = 0;
        for (OrderImage image : images) {
            if (Files.exists(resolve(image.getImageUrl()))) {
                existing++;
            }
        }
        return existing;
    }

    private Path resolve(String imageUrl) {
        return Paths.get(UPLOAD_LOCATION).resolve(imageUrl.replaceFirst("^/uploads/", ""));
    }

    /** 删掉图片记录、订单记录与磁盘文件，避免污染 uploads/ 与后续用例 */
    private void cleanup(Long orderId) throws Exception {
        List<OrderImage> images = orderImageService.getByOrderId(orderId);
        for (OrderImage image : images) {
            Files.deleteIfExists(resolve(image.getImageUrl()));
        }
        orderImageService.remove(new LambdaQueryWrapper<OrderImage>().eq(OrderImage::getOrderId, orderId));
        repairOrderService.removeById(orderId);
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R', 0x00, 0x00, 0x00, 0x01
        };
    }
}

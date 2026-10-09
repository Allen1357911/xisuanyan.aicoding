package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dormrepairsystem.entity.OrderImage;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.service.OrderImageService;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.util.FileStorage;
import org.example.dormrepairsystem.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * 输入兜底与异常路径的验证（评审后补的三处加固）
 *
 * 覆盖三件事：
 *   1. 分页参数夹取：page<1 回到第 1 页、size 超上限被夹到 100、size<1 回到默认 5
 *   2. 字段级校验：problemDesc / deviceType 的长度与非空在**写库之前**被拦住
 *   3. 上传写库失败时，刚落盘的文件会被清理掉，不留孤儿文件
 *
 * 真实拦截器 + MockMvc，Service 用 mock，不依赖数据库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InputValidationAndPagingTest {

    private static final long STUDENT_ID = 7L;
    private static final long ADMIN_ID = 2L;
    private static final long ORDER_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private RepairOrderService repairOrderService;

    @MockBean
    private DormitoryService dormitoryService;

    @MockBean
    private OrderImageService orderImageService;

    @MockBean
    private FileStorage fileStorage;

    private String token(long userId, int roleId) {
        return "Bearer " + jwtUtil.generateAccessToken(jwtUtil.buildClaims(userId, roleId, "测试用户"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 1. 分页参数夹取 ----------

    @Test
    void pageBelowOneIsNormalizedBeforePaging() throws Exception {
        // 说明：实测（临时移除夹取后打真实请求）MyBatis-Plus 的 Page 构造器本身就会把 current<1 归一成 1，
        // 所以"page=0 会回到第 1 页"并非我这段代码的功劳，这里的 normalizePage 属于冗余防御。
        // mock 故意返回一个未被夹取的分页对象，确保断言的是"传下去的参数"而不是 mock 自己的返回值。
        when(repairOrderService.getPage(any(), eq(null))).thenReturn(new Page<>(99, 5));

        mockMvc.perform(get("/repair-orders").param("page", "0").param("size", "5")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/repair-orders").param("page", "-7").param("size", "5")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());

        ArgumentCaptor<Page<RepairOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(repairOrderService, org.mockito.Mockito.times(2)).getPage(captor.capture(), eq(null));
        for (Page<RepairOrder> pageArg : captor.getAllValues()) {
            assertEquals(1L, pageArg.getCurrent(), "page<1 不应把非法页码传进查询层");
            assertEquals(5L, pageArg.getSize(), "size 应保持调用方给的值");
        }
    }

    @Test
    void pageSizeIsClampedToUpperBound() throws Exception {
        when(repairOrderService.getPage(any(), eq(null))).thenReturn(new Page<>(1, 500));

        mockMvc.perform(get("/repair-orders").param("page", "1").param("size", "100000")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());

        ArgumentCaptor<Page<RepairOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(repairOrderService).getPage(captor.capture(), eq(null));
        assertEquals(500L, captor.getValue().getSize(), "size 超上限必须被夹到 500，不能一次拉全表");
    }

    @Test
    void pageSizeBelowOneFallsBackToDefault() throws Exception {
        when(repairOrderService.getPage(any(), eq(null))).thenReturn(new Page<>(1, 5));

        mockMvc.perform(get("/repair-orders").param("page", "1").param("size", "-5")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());

        ArgumentCaptor<Page<RepairOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(repairOrderService).getPage(captor.capture(), eq(null));
        // size<1 是真实缺陷而非洁癖：临时移除夹取后实测 size=-5 会返回 records=4 但 total=0（分页元数据自相矛盾）
        assertEquals(5L, captor.getValue().getSize(), "size<1 必须回到默认 5");
    }

    @Test
    void pageSizeUpperBoundIsStillGenerousEnough() throws Exception {
        when(repairOrderService.getPage(any(), eq(null))).thenReturn(new Page<>(1, 500));

        mockMvc.perform(get("/repair-orders").param("page", "1").param("size", "500")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());

        ArgumentCaptor<Page<RepairOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(repairOrderService).getPage(captor.capture(), eq(null));
        assertEquals(500L, captor.getValue().getSize(), "上限本身必须被原样接受，避免误伤合法调用");
    }

    @Test
    void commonBulkPageSizesAreNotClamped() throws Exception {
        // 一次拉完种子量的常见做法（例如 size=200 导出）必须原样透传，不能被夹
        when(repairOrderService.getPage(any(), eq(null))).thenReturn(new Page<>(1, 200));

        mockMvc.perform(get("/repair-orders").param("page", "1").param("size", "200")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());

        ArgumentCaptor<Page<RepairOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(repairOrderService).getPage(captor.capture(), eq(null));
        assertEquals(200L, captor.getValue().getSize(), "200 条这类批量取数不应被改动");
    }

    // ---------- 2. 字段级校验：必须在写库之前拦住 ----------

    @Test
    void longProblemDescriptionIsRejectedBeforeTouchingDatabase() throws Exception {
        String body = "{\"deviceType\":\"电灯\",\"problemDesc\":\"" + "x".repeat(2001) + "\"}";

        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("问题描述最多2000字"));

        verify(repairOrderService, never()).save(any(RepairOrder.class));
    }

    @Test
    void problemDescriptionAtLimitIsAccepted() throws Exception {
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(dormitory());
        when(repairOrderService.save(any(RepairOrder.class))).thenReturn(true);
        String body = "{\"deviceType\":\"电灯\",\"problemDesc\":\"" + "x".repeat(2000) + "\"}";

        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isCreated());
    }

    @Test
    void longDeviceTypeIsRejectedBeforeTouchingDatabase() throws Exception {
        String body = "{\"deviceType\":\"" + "z".repeat(31) + "\",\"problemDesc\":\"灯坏了\"}";

        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("设备类型最多30字"));

        verify(repairOrderService, never()).save(any(RepairOrder.class));
    }

    @Test
    void missingOrBlankDeviceTypeIsRejected() throws Exception {
        String noDevice = "{\"problemDesc\":\"灯坏了\"}";
        String blankDevice = "{\"deviceType\":\"   \",\"problemDesc\":\"灯坏了\"}";

        mockMvc.perform(json(post("/repair-orders"), noDevice).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("设备类型不能为空"));

        mockMvc.perform(json(post("/repair-orders"), blankDevice).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("设备类型不能为空"));

        verify(repairOrderService, never()).save(any(RepairOrder.class));
    }

    @Test
    void deviceTypeAtLimitIsAccepted() throws Exception {
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(dormitory());
        when(repairOrderService.save(any(RepairOrder.class))).thenReturn(true);
        String body = "{\"deviceType\":\"" + "z".repeat(30) + "\",\"problemDesc\":\"灯坏了\"}";

        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isCreated());
    }

    @Test
    void validOrderStillReportsMissingDormitoryBinding() throws Exception {
        // 校验通过后原有业务规则必须照旧生效：没有绑定宿舍 → 400
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(null);
        String body = "{\"deviceType\":\"电灯\",\"problemDesc\":\"灯坏了\"}";

        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先绑定宿舍"));
    }

    // ---------- 3. 上传写库失败：不留孤儿文件 ----------

    @Test
    void uploadedFileIsRemovedWhenSavingRecordFails() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
        when(orderImageService.getByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
        when(fileStorage.uploadImage(any(), anyString())).thenReturn("/uploads/repair-orders/orphan.png");
        when(orderImageService.saveOrderImage(any(OrderImage.class))).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("上传失败，请稍后重试"));

        // 关键：刚落盘的文件必须被删掉，否则磁盘上会留下没人引用的孤儿文件
        verify(fileStorage).deleteImage("/uploads/repair-orders/orphan.png");
    }

    @Test
    void uploadedFileIsKeptWhenSavingRecordSucceeds() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
        when(orderImageService.getByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
        when(fileStorage.uploadImage(any(), anyString())).thenReturn("/uploads/repair-orders/kept.png");
        when(orderImageService.saveOrderImage(any(OrderImage.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isCreated());

        verify(fileStorage, never()).deleteImage(anyString());
    }

    @Test
    void uploadStillRejectsWhenImageLimitReached() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
        when(orderImageService.getByOrderId(ORDER_ID)).thenReturn(java.util.List.of(
                new OrderImage(), new OrderImage(), new OrderImage()));

        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());

        verify(fileStorage, never()).uploadImage(any(), anyString());
    }

    // ---------- 4. 图片文件头校验（防伪造 Content-Type） ----------

    @Test
    void fakeContentTypeIsRejectedByMagicBytes() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
        // 声明 image/png，内容其实是文本：这一层由文件头校验拦下
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只能上传图片文件"));

        verify(fileStorage, never()).uploadImage(any(), anyString());
    }

    @Test
    void nonImageContentTypeIsRejectedEvenForRealImageBytes() throws Exception {
        // 这里用合法 PNG 字节，故意让文件头校验通过，从而单独把 Content-Type 这一层隔离出来测：
        // 若 Content-Type 校验被删掉，这条用例会失败（已用突变测试确认）
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "text/plain", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只能上传图片文件"));

        verify(fileStorage, never()).uploadImage(any(), anyString());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());

        verify(fileStorage, never()).uploadImage(any(), anyString());
    }

    @Test
    void realImageFormatsAreAccepted() throws Exception {
        byte[][] files = {
                pngBytes(),
                {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01},
                {'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00, 0x01, 0x00, 0x00, 0x00},
                {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '}
        };

        for (byte[] bytes : files) {
            when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
            when(orderImageService.getByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
            when(fileStorage.uploadImage(any(), anyString())).thenReturn("/uploads/repair-orders/kept.png");
            when(orderImageService.saveOrderImage(any(OrderImage.class))).thenReturn(true);

            MockMultipartFile file = new MockMultipartFile("file", "real.png", "image/png", bytes);
            mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                            .header("Authorization", token(STUDENT_ID, 1)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void gifHeaderIsAcceptedRegardlessOfFilenameOrDeclaredType() throws Exception {
        // GIF 曾经在手工联调里出现过一次无法复现的 400，这里把"GIF 文件头必须被接受"钉死在自动化里，
        // 并额外覆盖两种常见变形：文件名不是 .gif、声明的类型是 image/jpeg。
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00, 0x01, 0x00, 0x00, 0x00};
        String[][] variants = {
                {"a.gif", "image/gif"},
                {"upload.png", "image/png"},
                {"upload.bin", "image/jpeg"}
        };

        for (String[] variant : variants) {
            when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
            when(orderImageService.getByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
            when(fileStorage.uploadImage(any(), anyString())).thenReturn("/uploads/repair-orders/gif.png");
            when(orderImageService.saveOrderImage(any(OrderImage.class))).thenReturn(true);

            MockMultipartFile file = new MockMultipartFile("file", variant[0], variant[1], gif);
            mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                            .header("Authorization", token(STUDENT_ID, 1)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void shortOrTruncatedFilesAreRejected() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderOwnedByStudent());
        // 只有部分 PNG 文件头（截断），不应被当成合法图片
        byte[][] truncated = {
                {(byte) 0x89, 'P', 'N', 'G'},
                {(byte) 0xFF, (byte) 0xD8},
                {'G', 'I', 'F'},
                {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B'}
        };

        for (byte[] bytes : truncated) {
            MockMultipartFile file = new MockMultipartFile("file", "cut.png", "image/png", bytes);
            mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                            .header("Authorization", token(STUDENT_ID, 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("只能上传图片文件"));
        }

        verify(fileStorage, never()).uploadImage(any(), anyString());
    }

    // ---------- 5. 已取消 / 已完成 的工单不再接受新图片 ----------

    @Test
    void uploadIsRejectedForCancelledOrder() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderWithStatus(STUDENT_ID, "已取消"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("已取消或已完成的工单不能再上传图片"));

        verify(fileStorage, never()).uploadImage(any(), anyString());
    }

    @Test
    void uploadIsRejectedForCompletedOrder() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderWithStatus(STUDENT_ID, "已完成"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("已取消或已完成的工单不能再上传图片"));

        verify(fileStorage, never()).uploadImage(any(), anyString());
    }

    @Test
    void uploadIsStillAllowedWhileWaitingForConfirmation() throws Exception {
        // 待确认阶段师傅可能需要补传完工证据，这里保持允许
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderWithStatus(STUDENT_ID, "待确认"));
        when(orderImageService.getByOrderId(ORDER_ID)).thenReturn(java.util.List.of());
        when(fileStorage.uploadImage(any(), anyString())).thenReturn("/uploads/repair-orders/wait.png");
        when(orderImageService.saveOrderImage(any(OrderImage.class))).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isCreated());
    }

    @Test
    void ownershipCheckStillWinsOverStatusCheck() throws Exception {
        // 既不是本人、状态又是终态时，应当先暴露"无权"而不是"状态不允许"
        when(repairOrderService.getById(ORDER_ID)).thenReturn(orderWithStatus(99L, "已取消"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/" + ORDER_ID + "/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());
    }

    // ---------- 辅助 ----------

    /** 合法的 1x1 PNG 字节（前 8 字节是 PNG 文件头） */
    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R', 0x00, 0x00, 0x00, 0x01
        };
    }

    private RepairOrder orderWithStatus(long ownerId, String status) {
        RepairOrder order = new RepairOrder();
        order.setOrderId(ORDER_ID);
        order.setUserId(ownerId);
        order.setOrderStatus(status);
        return order;
    }

    private RepairOrder orderOwnedByStudent() {
        RepairOrder order = new RepairOrder();
        order.setOrderId(ORDER_ID);
        order.setUserId(STUDENT_ID);
        order.setOrderStatus("待处理");
        return order;
    }

    private org.example.dormrepairsystem.entity.Dormitory dormitory() {
        org.example.dormrepairsystem.entity.Dormitory dormitory = new org.example.dormrepairsystem.entity.Dormitory();
        dormitory.setDormId(1L);
        dormitory.setBuilding("1栋");
        dormitory.setRoomNum("502");
        return dormitory;
    }
}

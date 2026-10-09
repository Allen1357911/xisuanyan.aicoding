package org.example.dormrepairsystem;

import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.service.OrderImageService;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.util.JwtUtil;
import org.example.dormrepairsystem.util.FileStorage;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制器归属校验与状态机验证（真实拦截器 + MockMvc，service 用 mock，不连数据库）
 */
@SpringBootTest
@AutoConfigureMockMvc
class RepairOrderAuthorizationTest {

    private static final long STUDENT_ID = 7L;
    private static final long REPAIRMAN_ID = 8L;
    private static final long ADMIN_ID = 2L;
    private static final long OTHER_ID = 99L;

    private static final String CANCEL = "{\"status\":\"已取消\"}";
    private static final String COMPLETE = "{\"status\":\"已完成\"}";
    private static final String REPAIRING = "{\"status\":\"维修中\"}";

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

    private RepairOrder order(long ownerId, Long repairmanId, String status) {
        RepairOrder order = new RepairOrder();
        order.setOrderId(5L);
        order.setUserId(ownerId);
        order.setRepairmanId(repairmanId);
        order.setOrderStatus(status);
        return order;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 提交报修 ----------

    @Test
    void createOrderUsesTokenUserIdAndIgnoresBody() throws Exception {
        Dormitory dormitory = new Dormitory();
        dormitory.setDormId(3L);
        dormitory.setBuilding("1栋");
        dormitory.setRoomNum("502");
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(dormitory);
        when(repairOrderService.save(any(RepairOrder.class))).thenReturn(true);

        String body = "{\"userId\":999,\"repairmanId\":888,\"orderStatus\":\"已完成\","
                + "\"deviceType\":\"灯\",\"problemDesc\":\"灯不亮\"}";

        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isCreated());

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(repairOrderService).save(captor.capture());
        RepairOrder saved = captor.getValue();
        assertEquals(STUDENT_ID, saved.getUserId(), "报修人必须是令牌中的用户");
        assertNull(saved.getRepairmanId(), "提交报修不能自带维修人员");
        assertEquals("待处理", saved.getOrderStatus(), "初始状态必须由服务端决定");
        assertEquals(3L, saved.getDormId());
        assertEquals("1栋", saved.getBuilding());
    }

    @Test
    void createOrderRequiresDormitoryBinding() throws Exception {
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(null);
        String body = "{\"deviceType\":\"灯\",\"problemDesc\":\"灯不亮\"}";

        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());

        verify(repairOrderService, never()).save(any(RepairOrder.class));
    }

    @Test
    void createOrderRequiresProblemDescription() throws Exception {
        String body = "{\"deviceType\":\"灯\"}";
        mockMvc.perform(json(post("/repair-orders"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 修改状态 ----------

    @Test
    void studentCannotUpdateOthersOrder() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, null, "待处理"));

        mockMvc.perform(json(put("/repair-orders/5/status"), CANCEL).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void studentCanCancelOwnPendingOrder() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(STUDENT_ID, null, "待处理"));
        when(repairOrderService.updateById(any(RepairOrder.class))).thenReturn(true);

        mockMvc.perform(json(put("/repair-orders/5/status"), CANCEL).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isOk());

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(repairOrderService).updateById(captor.capture());
        assertEquals("已取消", captor.getValue().getOrderStatus());
        assertNotNull(captor.getValue().getUpdateTime(), "改状态必须刷新 updateTime");
    }

    @Test
    void studentCannotConfirmOrderThatIsNotRepairing() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(STUDENT_ID, null, "待处理"));

        mockMvc.perform(json(put("/repair-orders/5/status"), COMPLETE).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotSetRepairingStatus() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(STUDENT_ID, null, "待处理"));

        mockMvc.perform(json(put("/repair-orders/5/status"), REPAIRING).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatusValueIsRejected() throws Exception {
        mockMvc.perform(json(put("/repair-orders/5/status"), "{\"status\":\"已维修\"}")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repairmanCannotUpdateOthersOrder() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, OTHER_ID, "维修中"));

        mockMvc.perform(json(put("/repair-orders/5/status"), COMPLETE).header("Authorization", token(REPAIRMAN_ID, 3)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanMoveAnyOrderBackToPending() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, OTHER_ID, "已完成"));
        when(repairOrderService.updateById(any(RepairOrder.class))).thenReturn(true);

        mockMvc.perform(json(put("/repair-orders/5/status"), "{\"status\":\"待处理\"}")
                        .header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());
    }

    // ---------- 接单 ----------

    @Test
    void acceptOrderUsesTokenRepairmanId() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, null, "待处理"));
        when(repairOrderService.acceptOrder(5L, REPAIRMAN_ID)).thenReturn(true);

        String body = "{\"repairmanId\":888}";
        mockMvc.perform(json(post("/repair-orders/5/accept"), body).header("Authorization", token(REPAIRMAN_ID, 3)))
                .andExpect(status().isOk());

        verify(repairOrderService).acceptOrder(5L, REPAIRMAN_ID);
    }

    @Test
    void acceptOrderRejectsFinishedOrder() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, OTHER_ID, "已完成"));

        mockMvc.perform(post("/repair-orders/5/accept").header("Authorization", token(REPAIRMAN_ID, 3)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 图片上传 ----------

    @Test
    void imageUploadRejectsNonImageFile() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(STUDENT_ID, null, "待处理"));
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/repair-orders/5/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageUploadRequiresOrderOwnership() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, null, "待处理"));
        // 必须用带 PNG 文件头的字节，否则会先被文件头校验拦掉（400），根本走不到归属校验（403）
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/repair-orders/5/images").file(file)
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());

        // 归属校验必须发生在文件落盘之前，否则越权请求会先在服务器上留下文件
        verify(fileStorage, never()).uploadImage(any(), any());
    }

    /** 带合法 PNG 文件头的字节序列（校验只看前 8 字节） */
    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R', 0x00, 0x00, 0x00, 0x01
        };
    }

    // ---------- 删除订单 ----------

    @Test
    void studentCannotDeleteOthersOrder() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, null, "待处理"));

        mockMvc.perform(delete("/repair-orders/5").header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());

        verify(repairOrderService, never()).deleteOrderWithImages(anyLong());
    }

    @Test
    void studentCannotDeleteOrderThatIsBeingRepaired() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(STUDENT_ID, null, "维修中"));

        mockMvc.perform(delete("/repair-orders/5").header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanDeleteOrderAndImages() throws Exception {
        when(repairOrderService.getById(5L)).thenReturn(order(OTHER_ID, null, "已完成"));
        when(repairOrderService.deleteOrderWithImages(5L)).thenReturn(true);

        mockMvc.perform(delete("/repair-orders/5").header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());

        verify(repairOrderService).deleteOrderWithImages(5L);
    }

    // ---------- 宿舍 ----------

    @Test
    void studentCannotReadOthersDormitory() throws Exception {
        mockMvc.perform(get("/dormitories").param("userId", String.valueOf(OTHER_ID))
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotBindDormitoryForOthers() throws Exception {
        String body = "{\"userId\":99,\"building\":\"1栋\",\"roomNum\":\"502\"}";

        mockMvc.perform(json(post("/dormitories"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());

        verify(dormitoryService, never()).save(any(Dormitory.class));
    }

    private static void assertNotNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNotNull(value, message);
    }
}

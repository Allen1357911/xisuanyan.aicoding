package org.example.dormrepairsystem;

import org.example.dormrepairsystem.dto.DeviceTypeStatDTO;
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
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「完工确认」状态机与设备类型统计接口的契约验证
 *
 * 真实拦截器 + MockMvc，Service 用 mock，不依赖数据库；统计口径的数据正确性
 * 由 DeviceTypeStatsIntegrationTest 在真实 H2 上覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RepairOrderStateMachineAndStatsTest {

    private static final long STUDENT_ID = 7L;
    private static final long OTHER_STUDENT_ID = 99L;
    private static final long REPAIRMAN_ID = 8L;
    private static final long ADMIN_ID = 2L;
    private static final long ORDER_ID = 5L;

    /** 新状态：维修人员提交完工，等待报修学生验收 */
    private static final String PENDING_CONFIRM = "待确认";

    private static final String PENDING = "{\"status\":\"待处理\"}";
    private static final String REPAIRING = "{\"status\":\"维修中\"}";
    private static final String TO_PENDING_CONFIRM = "{\"status\":\"待确认\"}";
    private static final String COMPLETE = "{\"status\":\"已完成\"}";
    private static final String CANCEL = "{\"status\":\"已取消\"}";

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

    private String student() {
        return token(STUDENT_ID, 1);
    }

    private String repairman() {
        return token(REPAIRMAN_ID, 3);
    }

    private String admin() {
        return token(ADMIN_ID, 2);
    }

    private RepairOrder order(long ownerId, Long repairmanId, String status) {
        RepairOrder order = new RepairOrder();
        order.setOrderId(ORDER_ID);
        order.setUserId(ownerId);
        order.setRepairmanId(repairmanId);
        order.setOrderStatus(status);
        return order;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 维修人员：不能再直接把工单置为已完成 ----------

    @Test
    void repairmanCannotSetOrderCompletedDirectly() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(OTHER_STUDENT_ID, REPAIRMAN_ID, "维修中"));

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), COMPLETE)
                        .header("Authorization", repairman()))
                .andExpect(status().isForbidden());

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void repairmanCannotSetPendingOrConfirmedOrderCompleted() throws Exception {
        for (String current : new String[]{"待处理", "待确认"}) {
            when(repairOrderService.getById(ORDER_ID)).thenReturn(order(OTHER_STUDENT_ID, REPAIRMAN_ID, current));

            mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), COMPLETE)
                            .header("Authorization", repairman()))
                    .andExpect(status().isForbidden());
        }

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    // ---------- 维修人员：只有维修中可以提交为待确认 ----------

    @Test
    void repairmanSubmitsRepairingOrderForConfirmation() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(OTHER_STUDENT_ID, REPAIRMAN_ID, "维修中"));
        when(repairOrderService.updateById(any(RepairOrder.class))).thenReturn(true);

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), TO_PENDING_CONFIRM)
                        .header("Authorization", repairman()))
                .andExpect(status().isOk());

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(repairOrderService).updateById(captor.capture());
        assertEquals(PENDING_CONFIRM, captor.getValue().getOrderStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getUpdateTime(),
                "改状态必须刷新 updateTime");
    }

    @Test
    void repairmanCannotSubmitNonRepairingOrderForConfirmation() throws Exception {
        for (String current : new String[]{"待处理", "待确认", "已完成", "已取消"}) {
            when(repairOrderService.getById(ORDER_ID)).thenReturn(order(OTHER_STUDENT_ID, REPAIRMAN_ID, current));

            mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), TO_PENDING_CONFIRM)
                            .header("Authorization", repairman()))
                    .andExpect(status().isBadRequest());
        }

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void repairmanCannotAdvanceOthersOrder() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(OTHER_STUDENT_ID, 999L, "维修中"));

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), TO_PENDING_CONFIRM)
                        .header("Authorization", repairman()))
                .andExpect(status().isForbidden());
    }

    // ---------- 学生：只能确认待确认的工单 ----------

    @Test
    void ownerStudentConfirmsPendingConfirmOrder() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(STUDENT_ID, REPAIRMAN_ID, PENDING_CONFIRM));
        when(repairOrderService.updateById(any(RepairOrder.class))).thenReturn(true);

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), COMPLETE)
                        .header("Authorization", student()))
                .andExpect(status().isOk());

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(repairOrderService).updateById(captor.capture());
        assertEquals("已完成", captor.getValue().getOrderStatus());
    }

    @Test
    void studentCannotSkipRepairmanAndConfirmRepairingOrder() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(STUDENT_ID, REPAIRMAN_ID, "维修中"));

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), COMPLETE)
                        .header("Authorization", student()))
                .andExpect(status().isBadRequest());

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void otherStudentCannotConfirmSomeoneElsesOrder() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(OTHER_STUDENT_ID, REPAIRMAN_ID, PENDING_CONFIRM));

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), COMPLETE)
                        .header("Authorization", student()))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotCancelPendingConfirmOrder() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(STUDENT_ID, REPAIRMAN_ID, PENDING_CONFIRM));

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), CANCEL)
                        .header("Authorization", student()))
                .andExpect(status().isBadRequest());

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void studentCannotPutOrderIntoPendingConfirmHimself() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(STUDENT_ID, REPAIRMAN_ID, "维修中"));

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), TO_PENDING_CONFIRM)
                        .header("Authorization", student()))
                .andExpect(status().isForbidden());
    }

    // ---------- 保留的既有规则 ----------

    @Test
    void studentCanStillCancelPendingAndRepairingOrders() throws Exception {
        for (String current : new String[]{"待处理", "维修中"}) {
            when(repairOrderService.getById(ORDER_ID)).thenReturn(order(STUDENT_ID, REPAIRMAN_ID, current));
            when(repairOrderService.updateById(any(RepairOrder.class))).thenReturn(true);

            mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), CANCEL)
                            .header("Authorization", student()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void studentStillCannotSetRepairingStatus() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(order(STUDENT_ID, REPAIRMAN_ID, "待处理"));

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), REPAIRING)
                        .header("Authorization", student()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanMoveAnyOrderToAnyValidStatus() throws Exception {
        for (String target : new String[]{"待处理", "维修中", "待确认", "已完成", "已取消"}) {
            when(repairOrderService.getById(ORDER_ID)).thenReturn(order(OTHER_STUDENT_ID, REPAIRMAN_ID, "已完成"));
            when(repairOrderService.updateById(any(RepairOrder.class))).thenReturn(true);

            mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), "{\"status\":\"" + target + "\"}")
                            .header("Authorization", admin()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void invalidStatusValueIsStillRejected() throws Exception {
        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), "{\"status\":\"已维修\"}")
                        .header("Authorization", admin()))
                .andExpect(status().isBadRequest());

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        when(repairOrderService.getById(ORDER_ID)).thenReturn(null);

        mockMvc.perform(json(put("/repair-orders/" + ORDER_ID + "/status"), TO_PENDING_CONFIRM)
                        .header("Authorization", admin()))
                .andExpect(status().isNotFound());

        verify(repairOrderService, never()).updateById(any(RepairOrder.class));
    }

    // ---------- T3 统计接口：权限与返回契约 ----------

    @Test
    void adminGetsDeviceTypeStatsInContractFormat() throws Exception {
        DeviceTypeStatDTO first = stat("水龙头", 3L);
        DeviceTypeStatDTO second = stat("电灯", 1L);
        when(repairOrderService.countByDeviceType()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/repair-orders/stats/device-type").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].deviceType").value("水龙头"))
                .andExpect(jsonPath("$.data[0].count").value(3))
                .andExpect(jsonPath("$.data[1].deviceType").value("电灯"))
                .andExpect(jsonPath("$.data[1].count").value(1));
    }

    @Test
    void emptyStatsAreReturnedAsEmptyArrayNot404() throws Exception {
        when(repairOrderService.countByDeviceType()).thenReturn(List.of());

        mockMvc.perform(get("/repair-orders/stats/device-type").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void deviceTypeStatsIsAdminOnly() throws Exception {
        mockMvc.perform(get("/repair-orders/stats/device-type").header("Authorization", student()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/repair-orders/stats/device-type").header("Authorization", repairman()))
                .andExpect(status().isForbidden());

        verify(repairOrderService, never()).countByDeviceType();
    }

    @Test
    void deviceTypeStatsRequiresToken() throws Exception {
        mockMvc.perform(get("/repair-orders/stats/device-type"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statsEndpointDoesNotDisturbOrderQueries() throws Exception {
        when(repairOrderService.getByRepairmanId(anyLong(), any())).thenReturn(List.of());

        mockMvc.perform(get("/repair-orders").param("repairmanId", String.valueOf(REPAIRMAN_ID))
                        .header("Authorization", repairman()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(repairOrderService).getByRepairmanId(REPAIRMAN_ID, null);
    }

    private DeviceTypeStatDTO stat(String deviceType, Long count) {
        DeviceTypeStatDTO dto = new DeviceTypeStatDTO();
        dto.setDeviceType(deviceType);
        dto.setCount(count);
        return dto;
    }
}

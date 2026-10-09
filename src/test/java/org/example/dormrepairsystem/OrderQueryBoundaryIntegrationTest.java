package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dormrepairsystem.entity.RepairOrder;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 查询边界的端到端验证（真实拦截器 + 真实数据库，Service 不打桩）
 *
 * 补齐三类此前只有手工证据或完全没有证据的边界：
 *   1. 空分页：页码超出末页时应返回空 records，但 total 仍如实
 *   2. 逗号分隔的多状态筛选：含空格、结尾多余逗号、未知状态
 *   3. 分页参数的端点行为：page=abc 的类型错误仍走 400，size 上限被夹取
 *
 * 注：并发上传突破 3 张图片上限属于已知限制，不在本类覆盖范围内（见提交说明）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderQueryBoundaryIntegrationTest {

    private static final String STATUS_DONE = "已完成";
    private static final String STATUS_CANCEL = "已取消";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RepairOrderService repairOrderService;

    private String adminToken() {
        return "Bearer " + jwtUtil.generateAccessToken(jwtUtil.buildClaims(1L, 2, "系统管理员"));
    }

    // ---------- 空分页 ----------

    @Test
    void pageBeyondLastPageReturnsEmptyRecordsButHonestTotalOverHttp() throws Exception {
        mockMvc.perform(get("/repair-orders").param("page", "9999").param("size", "10")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(4));
    }

    @Test
    void emptyResultSetIsReturnedAsEmptyArrayNotError() throws Exception {
        mockMvc.perform(get("/repair-orders").param("status", "不存在的状态")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------- 逗号分隔的多状态筛选 ----------

    @Test
    void commaSeparatedStatusFilterOverHttp() throws Exception {
        // 种子里已完成 1 条（工单 3）、已取消 1 条（工单 4）
        mockMvc.perform(get("/repair-orders").param("status", STATUS_DONE + "," + STATUS_CANCEL)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 带空格
        mockMvc.perform(get("/repair-orders").param("status", STATUS_DONE + ", " + STATUS_CANCEL)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 结尾多余逗号不应产生空条件
        mockMvc.perform(get("/repair-orders").param("status", STATUS_DONE + "," + STATUS_CANCEL + ",")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 单状态仍走 eq
        mockMvc.perform(get("/repair-orders").param("status", STATUS_DONE)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void multiStatusFilterAlsoWorksInPagedQuery() throws Exception {
        mockMvc.perform(get("/repair-orders").param("includeUserInfo", "true")
                        .param("status", STATUS_DONE + "," + STATUS_CANCEL)
                        .param("page", "1").param("size", "10")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    // ---------- 分页参数的端点行为 ----------

    @Test
    void typeMismatchOnPageStillReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/repair-orders").param("page", "abc")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void pageSizeIsClampedAtEndpointAndTotalStaysHonest() throws Exception {
        mockMvc.perform(get("/repair-orders").param("page", "1").param("size", "100000")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(500))
                .andExpect(jsonPath("$.data.total").value(4));
    }

    @Test
    void nonPositivePageSizeFallsBackToDefaultAtEndpoint() throws Exception {
        mockMvc.perform(get("/repair-orders").param("page", "1").param("size", "-5")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.total").value(4));
    }

    // ---------- 直接对服务层的边界断言（真 SQL） ----------

    @Test
    void servicePageBeyondLastPageKeepsTotal() {
        IPage<RepairOrder> page = repairOrderService.getPage(new Page<>(9999, 10), null);

        assertTrue(page.getRecords().isEmpty());
        assertEquals(4, page.getTotal());
    }

    @Test
    void serviceMultiStatusFilterTrimsAndIgnoresEmptySegments() {
        assertEquals(2, repairOrderService.getByOrderStatus(STATUS_DONE + ", " + STATUS_CANCEL).size());
        assertEquals(2, repairOrderService.getByOrderStatus(STATUS_DONE + "," + STATUS_CANCEL + ",").size());
        assertTrue(repairOrderService.getByOrderStatus("不存在的状态").isEmpty());
    }

    @Test
    void serviceSingleStatusFilterIsExact() {
        assertEquals(1, repairOrderService.getByOrderStatus(STATUS_DONE).size());
        assertEquals(1, repairOrderService.getByOrderStatus("待处理").size());
        // 未知状态不应抛异常
        assertTrue(repairOrderService.getByOrderStatus("已维修").isEmpty());
    }

    @Test
    void nullPageArgumentFailsFastInsteadOfQueryingEverything() {
        // getPage 的契约要求非空 Page（控制器总是传非空），这里钉住"传 null 会立刻失败"而不是
        // 静默退化成"查全表"。实测抛的是 NPE（MyBatis-Plus 内部），不是 IllegalArgumentException——
        // 两种都会被 GlobalExceptionHandler 兜成 400 而不是 500，所以这里只断言"会抛异常"，
        // 不去改产品代码做防御性判空（那会把调用方的编程错误藏起来）。
        assertThrows(RuntimeException.class, () -> repairOrderService.getPage(null, null));
    }
}

package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dormrepairsystem.entity.Dormitory;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分页兜底参数确实来自配置（application.yml 的 paging.default-size / paging.max-size）
 *
 * 这里故意用与默认值不同的配置启动上下文：
 *   - 若代码把上限写死在常量里，size=20 会被夹到 500（这里断言必须夹到 13，测试会失败）
 *   - 若默认值写死，size=-1 会回到 5（这里断言必须是 7）
 * 这样"上限可调"就不是一句说明，而是可验证的事实。
 */
@SpringBootTest(properties = {
        "paging.default-size=7",
        "paging.max-size=13"
})
@AutoConfigureMockMvc
class PagingConfigTest {

    private static final long ADMIN_ID = 2L;

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

    private String admin() {
        return "Bearer " + jwtUtil.generateAccessToken(jwtUtil.buildClaims(ADMIN_ID, 2, "系统管理员"));
    }

    @Test
    void configuredMaxSizeIsAppliedToOrderQuery() throws Exception {
        when(repairOrderService.getPage(any(), eq(null))).thenReturn(new Page<>(1, 13));

        mockMvc.perform(get("/repair-orders").param("size", "20").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(13));

        ArgumentCaptor<Page<RepairOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(repairOrderService).getPage(captor.capture(), eq(null));
        assertEquals(13L, captor.getValue().getSize(), "上限必须取配置值 13，而不是代码里的默认 500");
    }

    @Test
    void configuredDefaultSizeIsAppliedToOrderQuery() throws Exception {
        when(repairOrderService.getPage(any(), eq(null))).thenReturn(new Page<>(1, 7));

        mockMvc.perform(get("/repair-orders").param("size", "-1").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(7));

        ArgumentCaptor<Page<RepairOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(repairOrderService).getPage(captor.capture(), eq(null));
        assertEquals(7L, captor.getValue().getSize(), "默认值必须取配置值 7，而不是代码里的 5");
    }

    @Test
    void configuredMaxSizeIsAppliedToDormitoryQuery() throws Exception {
        when(dormitoryService.getDormitoryPage(any())).thenReturn(new Page<>(1, 13));

        mockMvc.perform(get("/dormitories").param("size", "1000").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(13));

        ArgumentCaptor<Page<Dormitory>> captor = ArgumentCaptor.forClass(Page.class);
        verify(dormitoryService).getDormitoryPage(captor.capture());
        assertEquals(13L, captor.getValue().getSize(), "宿舍接口同样必须取配置的上限");
    }
}

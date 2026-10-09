package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.dormrepairsystem.entity.Dormitory;
import org.example.dormrepairsystem.entity.User;
import org.example.dormrepairsystem.service.DormitoryService;
import org.example.dormrepairsystem.service.OrderImageService;
import org.example.dormrepairsystem.service.RepairOrderService;
import org.example.dormrepairsystem.service.UserService;
import org.example.dormrepairsystem.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户接口与宿舍接口的代码级验证（登录、注册、刷新令牌、删除级联入口、异常不泄露内部信息）
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAndDormitoryControllerTest {

    private static final long STUDENT_ID = 7L;
    private static final long ADMIN_ID = 2L;
    private static final long TARGET_ID = 9L;

    private static final String RAW_PASSWORD = "pw123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserService userService;

    @MockBean
    private DormitoryService dormitoryService;

    @MockBean
    private RepairOrderService repairOrderService;

    @MockBean
    private OrderImageService orderImageService;

    private String token(long userId, int roleId) {
        return "Bearer " + jwtUtil.generateAccessToken(jwtUtil.buildClaims(userId, roleId, "测试用户"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
    }

    private User student() {
        User user = new User();
        user.setUserId(STUDENT_ID);
        user.setAccount("3125");
        user.setUserName("学生甲");
        user.setRoleId(1);
        user.setPassword(new BCryptPasswordEncoder().encode(RAW_PASSWORD));
        return user;
    }

    // ---------- 登录 ----------

    @Test
    void loginReturnsTokensAndNeverLeaksPasswordHash() throws Exception {
        User user = student();
        String hash = user.getPassword();
        when(userService.getByAccount("3125")).thenReturn(user);

        String body = "{\"account\":\"3125\",\"password\":\"" + RAW_PASSWORD + "\"}";

        mockMvc.perform(json(post("/users/login"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.userId").value(STUDENT_ID))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(content().string(not(containsString(hash))))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        when(userService.getByAccount("3125")).thenReturn(student());
        String body = "{\"account\":\"3125\",\"password\":\"wrong-password\"}";

        mockMvc.perform(json(post("/users/login"), body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginRejectsBlankInput() throws Exception {
        mockMvc.perform(json(post("/users/login"), "{\"account\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 注册 ----------

    @Test
    void registerRejectsAccountWithBadPrefix() throws Exception {
        String body = "{\"account\":\"1123\",\"password\":\"pw123456\",\"userName\":\"张三\"}";

        mockMvc.perform(json(post("/users"), body))
                .andExpect(status().isBadRequest());

        verify(userService, never()).register(any(User.class));
    }

    @Test
    void registerRejectsMissingFields() throws Exception {
        mockMvc.perform(json(post("/users"), "{\"account\":\"3125\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerAcceptsStudentAccount() throws Exception {
        when(userService.register(any(User.class))).thenReturn(true);
        String body = "{\"account\":\"3125\",\"password\":\"pw123456\",\"userName\":\"张三\",\"roleId\":2}";

        mockMvc.perform(json(post("/users"), body))
                .andExpect(status().isCreated());

        // 角色由账号前缀在服务端决定，前端传的 roleId 不参与
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).register(captor.capture());
        assertEquals("3125", captor.getValue().getAccount());
    }

    // ---------- 刷新令牌 ----------

    @Test
    void refreshTokenReturnsNewAccessToken() throws Exception {
        String refreshToken = jwtUtil.generateRefreshToken(jwtUtil.buildClaims(STUDENT_ID, 1, "学生甲"));
        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(json(post("/users/refresh-token"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refreshTokenRejectsAccessTokenAndMissingValue() throws Exception {
        String accessToken = jwtUtil.generateAccessToken(jwtUtil.buildClaims(STUDENT_ID, 1, "学生甲"));

        mockMvc.perform(json(post("/users/refresh-token"), "{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(json(post("/users/refresh-token"), "{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 删除用户 ----------

    @Test
    void deleteUserForbidsNonAdminAndSelfDeletion() throws Exception {
        mockMvc.perform(delete("/users/" + TARGET_ID).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/users/" + ADMIN_ID).header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).deleteUser(any());
    }

    @Test
    void deleteUserSucceedsForAdmin() throws Exception {
        when(userService.deleteUser(TARGET_ID)).thenReturn(true);

        mockMvc.perform(delete("/users/" + TARGET_ID).header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk());

        verify(userService).deleteUser(TARGET_ID);
    }

    // ---------- 宿舍 ----------

    @Test
    void studentBindsOwnDormitory() throws Exception {
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(null);
        when(dormitoryService.save(any(Dormitory.class))).thenReturn(true);
        String body = "{\"userId\":" + STUDENT_ID + ",\"building\":\"1栋\",\"roomNum\":\"502\"}";

        mockMvc.perform(json(post("/dormitories"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isCreated());

        ArgumentCaptor<Dormitory> captor = ArgumentCaptor.forClass(Dormitory.class);
        verify(dormitoryService).save(captor.capture());
        assertEquals(STUDENT_ID, captor.getValue().getUserId());
    }

    @Test
    void studentUpdatesExistingDormitory() throws Exception {
        Dormitory existing = new Dormitory();
        existing.setDormId(3L);
        existing.setUserId(STUDENT_ID);
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(existing);
        when(dormitoryService.updateById(any(Dormitory.class))).thenReturn(true);
        String body = "{\"userId\":" + STUDENT_ID + ",\"building\":\"2栋\",\"roomNum\":\"301\"}";

        mockMvc.perform(json(post("/dormitories"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isOk());

        ArgumentCaptor<Dormitory> captor = ArgumentCaptor.forClass(Dormitory.class);
        verify(dormitoryService).updateById(captor.capture());
        assertEquals("2栋", captor.getValue().getBuilding());
        assertEquals("301", captor.getValue().getRoomNum());
    }

    @Test
    void bindDormitoryRequiresBuildingAndRoom() throws Exception {
        String body = "{\"userId\":" + STUDENT_ID + "}";

        mockMvc.perform(json(post("/dormitories"), body).header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentReadsOwnDormitory() throws Exception {
        when(dormitoryService.getByUserId(STUDENT_ID)).thenReturn(new Dormitory());

        mockMvc.perform(get("/dormitories").param("userId", String.valueOf(STUDENT_ID))
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void adminListsDormitoriesWithPaging() throws Exception {
        when(dormitoryService.getDormitoryPage(any())).thenReturn(new Page<Dormitory>(1, 5));

        mockMvc.perform(get("/dormitories").header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").exists());
    }

    // ---------- 状态筛选透传（修复“参数被静默忽略”） ----------

    @Test
    void studentOrderQueryPassesStatusToService() throws Exception {
        when(repairOrderService.getByUserId(eq(STUDENT_ID), eq("已完成"))).thenReturn(List.of());

        mockMvc.perform(get("/repair-orders").param("status", "已完成")
                        .header("Authorization", token(STUDENT_ID, 1)))
                .andExpect(status().isOk());

        verify(repairOrderService).getByUserId(STUDENT_ID, "已完成");
    }

    @Test
    void repairmanOrderQueryPassesStatusToService() throws Exception {
        when(repairOrderService.getByRepairmanId(eq(8L), eq("维修中"))).thenReturn(List.of());

        mockMvc.perform(get("/repair-orders").param("repairmanId", "8").param("status", "维修中")
                        .header("Authorization", token(8L, 3)))
                .andExpect(status().isOk());

        verify(repairOrderService).getByRepairmanId(8L, "维修中");
    }

    // ---------- 异常不泄露内部信息 ----------

    @Test
    void unexpectedExceptionDoesNotLeakInternals() throws Exception {
        when(repairOrderService.getById(5L)).thenThrow(new RuntimeException("内部错误：select * from user"));

        mockMvc.perform(get("/repair-orders/5/images").header("Authorization", token(ADMIN_ID, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("select * from user"))));
    }
}

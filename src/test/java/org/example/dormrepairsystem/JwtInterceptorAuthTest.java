package org.example.dormrepairsystem;

import io.jsonwebtoken.Claims;
import org.example.dormrepairsystem.interceptor.JwtInterceptor;
import org.example.dormrepairsystem.util.AuthUtil;
import org.example.dormrepairsystem.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权行为验证（真实 JWT + Mock 请求，不依赖数据库）
 */
class JwtInterceptorAuthTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 7200000L, 604800000L);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtil);

    private record Result(boolean allowed, int status, MockHttpServletRequest request) {
    }

    private Result call(String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, response, new Object());
        return new Result(allowed, response.getStatus(), request);
    }

    private String studentToken() {
        return jwtUtil.generateAccessToken(jwtUtil.buildClaims(7L, 1, "学生甲"));
    }

    private String repairmanToken() {
        return jwtUtil.generateAccessToken(jwtUtil.buildClaims(8L, 3, "维修乙"));
    }

    private String adminToken() {
        return jwtUtil.generateAccessToken(jwtUtil.buildClaims(2L, 2, "管理员"));
    }

    // ---------- 令牌本身 ----------

    @Test
    void rejectsMissingToken() throws Exception {
        assertEquals(401, call("GET", "/repair-orders", null).status());
    }

    @Test
    void rejectsGarbageToken() throws Exception {
        assertEquals(401, call("GET", "/repair-orders", "not-a-jwt").status());
    }

    @Test
    void rejectsRefreshTokenUsedAsAccessToken() throws Exception {
        String refreshToken = jwtUtil.generateRefreshToken(jwtUtil.buildClaims(7L, 1, "学生甲"));
        Result result = call("GET", "/repair-orders", refreshToken);
        assertFalse(result.allowed());
        assertEquals(401, result.status());
    }

    @Test
    void publicEndpointsDoNotRequireToken() throws Exception {
        assertTrue(call("POST", "/users/login", null).allowed());
        assertTrue(call("POST", "/users", null).allowed());
        assertTrue(call("POST", "/users/refresh-token", null).allowed());
    }

    @Test
    void userListIsNoLongerPublic() throws Exception {
        assertEquals(401, call("GET", "/users", null).status());
    }

    @Test
    void preflightRequestPasses() throws Exception {
        assertTrue(call("OPTIONS", "/repair-orders", null).allowed());
    }

    // ---------- 学生 ----------

    @Test
    void studentCanUseOwnOrderEndpoints() throws Exception {
        assertTrue(call("GET", "/repair-orders", studentToken()).allowed());
        assertTrue(call("POST", "/repair-orders", studentToken()).allowed());
        assertTrue(call("PUT", "/repair-orders/5/status", studentToken()).allowed());
        assertTrue(call("POST", "/repair-orders/5/images", studentToken()).allowed());
        assertTrue(call("GET", "/repair-orders/5/images", studentToken()).allowed());
        assertTrue(call("DELETE", "/repair-orders/images/3", studentToken()).allowed());
        assertTrue(call("DELETE", "/repair-orders/5", studentToken()).allowed());
        assertTrue(call("GET", "/dormitories", studentToken()).allowed());
        assertTrue(call("POST", "/dormitories", studentToken()).allowed());
    }

    @Test
    void studentCannotAcceptOrder() throws Exception {
        // 旧实现用 startsWith 前缀匹配，学生会顺带拿到接单权限
        Result result = call("POST", "/repair-orders/5/accept", studentToken());
        assertFalse(result.allowed());
        assertEquals(403, result.status());
    }

    @Test
    void studentCannotDeleteDormitoryOrListUsers() throws Exception {
        assertEquals(403, call("DELETE", "/dormitories/3", studentToken()).status());
        assertEquals(403, call("GET", "/users", studentToken()).status());
        assertEquals(403, call("DELETE", "/users/9", studentToken()).status());
    }

    // ---------- 维修人员 ----------

    @Test
    void repairmanCanAcceptAndAdvance() throws Exception {
        assertTrue(call("GET", "/repair-orders", repairmanToken()).allowed());
        assertTrue(call("POST", "/repair-orders/5/accept", repairmanToken()).allowed());
        assertTrue(call("PUT", "/repair-orders/5/status", repairmanToken()).allowed());
        assertTrue(call("GET", "/repair-orders/5/images", repairmanToken()).allowed());
    }

    @Test
    void repairmanCannotDeleteOrManageUsers() throws Exception {
        assertEquals(403, call("DELETE", "/repair-orders/5", repairmanToken()).status());
        assertEquals(403, call("GET", "/users", repairmanToken()).status());
        assertEquals(403, call("POST", "/dormitories", repairmanToken()).status());
    }

    // ---------- 管理员 ----------

    @Test
    void adminHasFullManagementAccess() throws Exception {
        assertTrue(call("GET", "/users", adminToken()).allowed());
        assertTrue(call("DELETE", "/users/9", adminToken()).allowed());
        assertTrue(call("GET", "/repair-orders", adminToken()).allowed());
        assertTrue(call("DELETE", "/repair-orders/5", adminToken()).allowed());
        assertTrue(call("PUT", "/repair-orders/5/status", adminToken()).allowed());
        assertTrue(call("GET", "/repair-orders/5/images", adminToken()).allowed());
        assertTrue(call("DELETE", "/repair-orders/images/4", adminToken()).allowed());
        assertTrue(call("GET", "/dormitories", adminToken()).allowed());
        assertTrue(call("DELETE", "/dormitories/3", adminToken()).allowed());
    }

    // ---------- 上下文传递与令牌解析 ----------

    @Test
    void authAttributesAreExposedToControllers() throws Exception {
        Result result = call("PUT", "/repair-orders/5/status", studentToken());
        assertTrue(result.allowed());
        assertEquals(7L, AuthUtil.currentUserId(result.request()));
        assertEquals(1, AuthUtil.currentRoleId(result.request()));
        assertTrue(AuthUtil.isStudent(result.request()));
        assertFalse(AuthUtil.isAdmin(result.request()));
    }

    @Test
    void claimsAreReadableAfterJsonRoundTrip() {
        Claims claims = jwtUtil.parseToken(studentToken());
        assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, claims.get(JwtUtil.CLAIM_TOKEN_TYPE));
        // JWT 里的数字反序列化后类型不确定，AuthUtil 必须能兼容
        assertEquals(7L, AuthUtil.toLong(claims.get(JwtUtil.CLAIM_USER_ID)));
        assertEquals(1, AuthUtil.toInteger(claims.get(JwtUtil.CLAIM_ROLE_ID)));
    }

    @Test
    void refreshFlowOnlyAcceptsRefreshToken() {
        String refreshToken = jwtUtil.generateRefreshToken(jwtUtil.buildClaims(7L, 1, "学生甲"));
        String newAccessToken = jwtUtil.refreshAccessToken(refreshToken);
        assertNotNull(newAccessToken);
        assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, jwtUtil.parseToken(newAccessToken).get(JwtUtil.CLAIM_TOKEN_TYPE));

        assertThrows(IllegalArgumentException.class, () -> jwtUtil.refreshAccessToken(studentToken()));
    }
}

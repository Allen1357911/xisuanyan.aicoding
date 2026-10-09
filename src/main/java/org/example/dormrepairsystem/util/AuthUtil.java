package org.example.dormrepairsystem.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户上下文
 *
 * JwtInterceptor 校验令牌通过后，会把用户ID和角色ID放进 request attribute，
 * 控制器统一从这里取当前身份，不再信任请求参数里传来的 userId / repairmanId。
 */
public final class AuthUtil {

    public static final String ATTR_USER_ID = "authUserId";
    public static final String ATTR_ROLE_ID = "authRoleId";

    /** 角色ID：学生 */
    public static final int ROLE_STUDENT = 1;
    /** 角色ID：管理员 */
    public static final int ROLE_ADMIN = 2;
    /** 角色ID：维修人员 */
    public static final int ROLE_REPAIRMAN = 3;

    private AuthUtil() {
    }

    public static Long currentUserId(HttpServletRequest request) {
        return toLong(request.getAttribute(ATTR_USER_ID));
    }

    public static Integer currentRoleId(HttpServletRequest request) {
        return toInteger(request.getAttribute(ATTR_ROLE_ID));
    }

    public static boolean isStudent(HttpServletRequest request) {
        return isRole(request, ROLE_STUDENT);
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return isRole(request, ROLE_ADMIN);
    }

    public static boolean isRepairman(HttpServletRequest request) {
        return isRole(request, ROLE_REPAIRMAN);
    }

    private static boolean isRole(HttpServletRequest request, int roleId) {
        Integer current = currentRoleId(request);
        return current != null && current == roleId;
    }

    /**
     * 宽松的数字转换：JWT 中的数字经过 JSON 反序列化后可能是 Integer，也可能是 Long
     */
    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer toInteger(Object value) {
        Long result = toLong(value);
        return result == null ? null : result.intValue();
    }
}

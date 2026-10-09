package org.example.dormrepairsystem.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dormrepairsystem.util.AuthUtil;
import org.example.dormrepairsystem.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JWT认证与授权拦截器
 *
 * 与旧实现相比：
 * 1. 权限按「HTTP方法 + 路径模式」精确匹配，不再用 startsWith 前缀匹配
 *    （避免 /repair-orders 顺带放行 /repair-orders/1/accept 之类的越权）；
 * 2. 登录、注册、刷新令牌以「方法+路径」白名单方式放行，不再整体排除 /users，
 *    避免 GET /users（用户列表）变成公开接口；
 * 3. 校验令牌类型，刷新令牌不能当访问令牌使用；
 * 4. 校验通过后把 userId / roleId 写入 request attribute，供控制器做归属校验。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /** 无需登录即可访问的接口（方法 + 路径） */
    private static final List<Permission> PUBLIC_ENDPOINTS = List.of(
            new Permission("POST", "/users/login"),
            new Permission("POST", "/users"),
            new Permission("POST", "/users/refresh-token")
    );

    /** 角色 -> 权限列表 */
    private static final Map<Integer, List<Permission>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        // 学生：提交报修、查看/维护自己的订单、绑定与查看自己的宿舍
        List<Permission> student = new ArrayList<>();
        student.add(new Permission("POST", "/repair-orders"));
        student.add(new Permission("GET", "/repair-orders"));
        student.add(new Permission("DELETE", "/repair-orders/{orderId}"));
        student.add(new Permission("PUT", "/repair-orders/{orderId}/status"));
        student.add(new Permission("GET", "/repair-orders/{orderId}/images"));
        student.add(new Permission("POST", "/repair-orders/{orderId}/images"));
        student.add(new Permission("DELETE", "/repair-orders/images/{imageId}"));
        student.add(new Permission("GET", "/dormitories"));
        student.add(new Permission("POST", "/dormitories"));
        student.add(new Permission("GET", "/role/**"));
        ROLE_PERMISSIONS.put(AuthUtil.ROLE_STUDENT, student);

        // 管理员：订单、用户、宿舍的全部管理权限
        List<Permission> admin = new ArrayList<>();
        admin.add(new Permission(null, "/repair-orders/**"));
        admin.add(new Permission("GET", "/users"));
        admin.add(new Permission("DELETE", "/users/{userId}"));
        admin.add(new Permission("GET", "/dormitories"));
        admin.add(new Permission("POST", "/dormitories"));
        admin.add(new Permission("DELETE", "/dormitories/{dormId}"));
        admin.add(new Permission("GET", "/role/**"));
        ROLE_PERMISSIONS.put(AuthUtil.ROLE_ADMIN, admin);

        // 维修人员：查看待处理订单、接单、推进自己接取的订单
        List<Permission> repairman = new ArrayList<>();
        repairman.add(new Permission("GET", "/repair-orders"));
        repairman.add(new Permission("POST", "/repair-orders/{orderId}/accept"));
        repairman.add(new Permission("PUT", "/repair-orders/{orderId}/status"));
        repairman.add(new Permission("GET", "/repair-orders/{orderId}/images"));
        repairman.add(new Permission("GET", "/role/**"));
        ROLE_PERMISSIONS.put(AuthUtil.ROLE_REPAIRMAN, repairman);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestPath = resolvePath(request);
        String method = request.getMethod();

        // 公开接口无需令牌
        if (matchesAny(PUBLIC_ENDPOINTS, method, requestPath)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.info("token不存在或格式错误：{} {}", method, requestPath);
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "未授权，请先登录");
        }

        String token = authorization.substring(7).trim();
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.info("token验证失败：{}", e.getMessage());
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "令牌无效或已过期");
        }

        // 刷新令牌不能当访问令牌使用
        if (!jwtUtil.isAccessToken(claims)) {
            log.info("使用了非访问令牌访问受保护资源：{} {}", method, requestPath);
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "令牌类型错误，请使用访问令牌");
        }

        Long userId = AuthUtil.toLong(claims.get(JwtUtil.CLAIM_USER_ID));
        Integer roleId = AuthUtil.toInteger(claims.get(JwtUtil.CLAIM_ROLE_ID));
        if (userId == null || roleId == null) {
            log.info("token中缺少用户信息：{} {}", method, requestPath);
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "令牌无效，缺少用户信息");
        }

        if (!hasPermission(roleId, method, requestPath)) {
            log.info("角色 {} 无权限访问 {} {}", roleId, method, requestPath);
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "权限不足，无法访问该资源");
        }

        request.setAttribute(AuthUtil.ATTR_USER_ID, userId);
        request.setAttribute(AuthUtil.ATTR_ROLE_ID, roleId);
        return true;
    }

    /**
     * 去掉上下文路径，得到用于匹配的请求路径
     */
    private String resolvePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private boolean hasPermission(Integer roleId, String method, String path) {
        List<Permission> permissions = ROLE_PERMISSIONS.get(roleId);
        return permissions != null && matchesAny(permissions, method, path);
    }

    private boolean matchesAny(List<Permission> permissions, String method, String path) {
        for (Permission permission : permissions) {
            if (permission.matches(method, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\": false, \"message\": \"" + message + "\"}");
        return false;
    }

    /**
     * 一条权限：HTTP方法（null表示任意方法）+ 路径模式
     */
    private static final class Permission {

        private final String method;
        private final Pattern pattern;

        private Permission(String method, String pathPattern) {
            this.method = method;
            this.pattern = toRegex(pathPattern);
        }

        private boolean matches(String httpMethod, String path) {
            if (method != null && !method.equalsIgnoreCase(httpMethod)) {
                return false;
            }
            return pattern.matcher(path).matches();
        }

        /**
         * 把 /repair-orders/{orderId}/images 这类模式转成全匹配正则：
         * {xxx} 只匹配单级路径，结尾的 /** 匹配多级路径。
         */
        private static Pattern toRegex(String pathPattern) {
            if (pathPattern.endsWith("/**")) {
                String prefix = pathPattern.substring(0, pathPattern.length() - 3);
                return Pattern.compile("^" + Pattern.quote(prefix) + "(/.*)?$");
            }

            StringBuilder regex = new StringBuilder("^");
            for (String segment : pathPattern.split("/", -1)) {
                // split 会把开头的 "/" 切出一个空段，直接跳过，否则会多拼出一个斜杠
                if (segment.isEmpty()) {
                    continue;
                }
                regex.append("/");
                if (segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2) {
                    regex.append("[^/]+");
                } else {
                    regex.append(Pattern.quote(segment));
                }
            }
            regex.append("$");
            return Pattern.compile(regex.toString());
        }
    }
}

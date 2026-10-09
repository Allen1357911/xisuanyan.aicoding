package org.example.dormrepairsystem;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.example.dormrepairsystem.config.MyBatisMetaObjectHandler;
import org.example.dormrepairsystem.interceptor.JwtInterceptor;
import org.example.dormrepairsystem.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上下文启动验证：确认各个 Bean 能正常装配，并跑通内嵌 H2 的建表与初始化数据
 */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private MetaObjectHandler metaObjectHandler;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @Test
    void contextLoadsWitAllBeans() {
        assertNotNull(metaObjectHandler, "MetaObjectHandler 未注册，createTime/updateTime 自动填充会失效");
        assertNotNull(jwtUtil, "JwtUtil 未被 Spring 管理");
        assertNotNull(jwtInterceptor, "JwtInterceptor 未被注入 WebConfig");
    }

    @Test
    void mybatisPlusReallyUsesTheMetaObjectHandler() {
        Object registered = GlobalConfigUtils.getGlobalConfig(sqlSessionFactory.getConfiguration())
                .getMetaObjectHandler();
        assertNotNull(registered, "MyBatis-Plus 没有接管 MetaObjectHandler，时间字段自动填充不会生效");
        assertTrue(registered instanceof MyBatisMetaObjectHandler,
                "注册进 MyBatis-Plus 的不是自定义的填充器：" + registered.getClass().getName());
    }

    @Test
    void paginationPluginIsRegistered() {
        boolean hasPagination = mybatisPlusInterceptor.getInterceptors().stream()
                .anyMatch(inner -> inner instanceof PaginationInnerInterceptor);
        assertTrue(hasPagination, "未注册分页插件，page() 会查全表且 total=0");
    }

    @Test
    void allEndpointsAreMapped() {
        var patterns = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .toList();

        assertTrue(patterns.contains("/repair-orders"), "报修单接口未注册：" + patterns);
        assertTrue(patterns.contains("/repair-orders/{orderId}/status"), "改状态接口未注册");
        assertTrue(patterns.contains("/repair-orders/{orderId}/images"), "图片接口未注册");
        assertTrue(patterns.contains("/users/login"), "登录接口未注册");
        assertTrue(patterns.contains("/users/refresh-token"), "刷新令牌接口未注册");
        assertTrue(patterns.contains("/dormitories"), "宿舍接口未注册");
    }
}

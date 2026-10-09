package org.example.dormrepairsystem.config;

import lombok.RequiredArgsConstructor;
import org.example.dormrepairsystem.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Value("${file.storage.location:uploads}")
    private String uploadLocation;

    @Value("${file.storage.url-prefix:/uploads}")
    private String uploadUrlPrefix;

    // 配置跨域请求
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")// 允许所有路径的跨域请求
                .allowedOriginPatterns("*")// 允许所有来源的跨域请求
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")// 允许所有HTTP方法
                .allowedHeaders("*")// 允许所有请求头
                // 认证走 Authorization 请求头而不是 Cookie，因此不开启 allowCredentials，
                // 避免出现 allowedOriginPatterns("*") + allowCredentials(true) 的高危组合
                .allowCredentials(false)
                .maxAge(3600);// 最大缓存时间，单位秒
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录、注册、刷新令牌的放行在 JwtInterceptor 内部按「方法 + 路径」白名单处理，
        // 这里不再整体排除 /users，否则 GET /users（用户列表）会变成公开接口
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/repair-orders/**", "/users/**", "/dormitories/**", "/role/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把本地图片目录映射成静态资源，这样 FileStorage 保存后返回的
        // /uploads/xxx.png 可以直接被前端 <img src> 访问
        registry.addResourceHandler(uploadUrlPrefix + "/**")
                .addResourceLocations("file:" + uploadLocation + "/");
    }
}

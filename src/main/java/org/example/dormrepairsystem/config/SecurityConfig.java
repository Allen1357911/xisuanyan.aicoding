package org.example.dormrepairsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护，因为我们的前端是静态HTML页面
            .csrf().disable()
            // 允许所有请求访问
            .authorizeRequests()
                .anyRequest().permitAll()
            // 禁用默认的登录页面
            .and()
            .formLogin().disable()
            .logout().disable();

        return http.build();
    }
}
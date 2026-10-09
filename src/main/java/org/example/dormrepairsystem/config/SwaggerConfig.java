package org.example.dormrepairsystem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public StandardReflectionParameterNameDiscoverer parameterNameDiscoverer() {
        return new StandardReflectionParameterNameDiscoverer();
    }//参数名发现器，让springdoc能通过反射获取接口方法的参数名
    
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("宿舍报修系统API文档")
                        .description("宿舍报修系统的RESTful API接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("开发者")
                                .email("developer@example.com")
                                .url("http://localhost:8082"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
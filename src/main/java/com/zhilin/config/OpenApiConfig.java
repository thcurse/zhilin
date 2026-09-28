package com.zhilin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 提供接口文档的基本说明，接口模型和参数约束由 springdoc 根据代码生成。 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置接口文档标题、响应契约说明和 Bearer 认证方式。
     *
     * @return 交给 springdoc 合并接口元数据的 OpenAPI 配置
     */
    @Bean
    public OpenAPI zhilinOpenApi() {
        return new OpenAPI().components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT"))).info(new Info()
                .title("知邻社区后端接口")
                .version("0.0.1-SNAPSHOT")
                .description("普通 JSON 接口返回 code、message、data；失败使用对应 HTTP 状态。"));
    }
}

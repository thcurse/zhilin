package com.zhilin.config;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Redis 键的命名空间配置，用前缀区分项目及测试数据，不替代 Redis 访问权限控制。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "zhilin.redis")
public class RedisKeyProperties {

    /** 项目键前缀，必须以冒号结尾；开发默认 zhilin:，测试使用 zhilin:test:。 */
    @NotBlank(message = "Redis 键前缀不能为空")
    @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9:_-]*:", message = "Redis 键前缀须以冒号结尾且不能含通配符")
    private String keyPrefix = "zhilin:";
}

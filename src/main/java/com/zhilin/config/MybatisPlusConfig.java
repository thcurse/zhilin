package com.zhilin.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 扫描按业务分包的 Mapper 接口，XML 路径和字段映射规则由应用配置维护。 */
@Configuration
@MapperScan("com.zhilin.mapper")
public class MybatisPlusConfig {
}

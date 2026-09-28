package com.zhilin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 知邻社区后端启动入口，自动扫描 com.zhilin 包下的组件。
 */
@SpringBootApplication
public class ZhilinApplication {

    /**
     * 启动应用并加载环境配置。
     *
     * @param args 命令行参数，可用于覆盖端口等应用配置
     */
    public static void main(String[] args) {
        SpringApplication.run(ZhilinApplication.class, args);
    }

}

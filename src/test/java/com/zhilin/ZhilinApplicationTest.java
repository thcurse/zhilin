package com.zhilin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证应用配置和组件能够加载，及时发现启动阶段的配置错误。
 */
@SpringBootTest(properties = "zhilin.auth.secret=test-only-signing-secret-never-use-in-deployment")
class ZhilinApplicationTest {

    /**
     * Spring 测试框架会先创建应用上下文，加载失败时此测试直接报错。
     */
    @Test
    void contextLoads() {
    }

}

package com.zhilin.config;

import com.zhilin.common.util.RedisKeyUtil;
import com.zhilin.entity.infrastructure.InfrastructureCheckEntity;
import com.zhilin.mapper.infrastructure.InfrastructureCheckMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实基础设施验收，需要显式传入 -DinfraTests=true。
 * 仅使用 zhilin_test 的连接级临时表及 zhilin:test: 下的随机 Redis 键。
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "infraTests", matches = "true")
class InfrastructureConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InfrastructureCheckMapper infrastructureCheckMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisKeyUtil redisKeyUtil;

    @Autowired
    private RedisKeyProperties redisKeyProperties;

    /** 用同一事务连接验证真实 MySQL CRUD、XML 和字符集，最后删除本连接的临时表。 */
    @Test
    @Transactional
    void shouldReadAndWriteMySqlWithMybatisPlus() {
        // 任何写入之前检查实际连接库，防止把开发库或旧业务库当作测试库。
        String actualDatabase = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertEquals("zhilin_test", actualDatabase, "集成测试只能连接 zhilin_test");

        jdbcTemplate.execute("""
                CREATE TEMPORARY TABLE stage3_connection_check (
                    id BIGINT PRIMARY KEY,
                    check_message VARCHAR(100) NOT NULL
                ) CHARACTER SET utf8mb4
                """);
        try {
            InfrastructureCheckEntity infrastructureCheckEntity = new InfrastructureCheckEntity();
            infrastructureCheckEntity.setId(1L);
            infrastructureCheckEntity.setCheckMessage("知邻连接验收😀");
            assertEquals(1, infrastructureCheckMapper.insert(infrastructureCheckEntity));

            InfrastructureCheckEntity storedInfrastructureCheckEntity = infrastructureCheckMapper.selectCheckById(1L);
            assertNotNull(storedInfrastructureCheckEntity);
            assertEquals(infrastructureCheckEntity.getCheckMessage(), storedInfrastructureCheckEntity.getCheckMessage());

            infrastructureCheckEntity.setCheckMessage("知邻更新验收");
            assertEquals(1, infrastructureCheckMapper.updateById(infrastructureCheckEntity));
            assertEquals("知邻更新验收", infrastructureCheckMapper.selectById(1L).getCheckMessage());

            assertEquals(1, infrastructureCheckMapper.deleteById(1L));
            assertNull(infrastructureCheckMapper.selectById(1L));
        } finally {
            jdbcTemplate.execute("DROP TEMPORARY TABLE stage3_connection_check");
        }
    }

    /** 验证真实 Redis 字符串读写、测试前缀与过期时间，只删除本次生成的随机键。 */
    @Test
    void shouldReadAndWriteRedisWithinTestNamespace() {
        assertEquals("zhilin:test:", redisKeyProperties.getKeyPrefix(), "集成测试只能使用测试前缀");
        // 随机键避免并行验收互相覆盖；即使进程中断，也会在 30 秒后过期。
        String checkKey = redisKeyUtil.build("infra:check:" + UUID.randomUUID());
        try {
            stringRedisTemplate.opsForValue().set(checkKey, "知邻 Redis 验收", Duration.ofSeconds(30));
            assertEquals("知邻 Redis 验收", stringRedisTemplate.opsForValue().get(checkKey));
            Long remainingSeconds = stringRedisTemplate.getExpire(checkKey);
            assertNotNull(remainingSeconds);
            assertTrue(remainingSeconds > 0 && remainingSeconds <= 30);
        } finally {
            stringRedisTemplate.delete(checkKey);
        }
        assertEquals(Boolean.FALSE, stringRedisTemplate.hasKey(checkKey));
    }
}

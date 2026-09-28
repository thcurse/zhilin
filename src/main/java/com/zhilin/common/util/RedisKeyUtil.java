package com.zhilin.common.util;

import com.zhilin.config.RedisKeyProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 统一拼接 Redis 键的项目或测试前缀；调用 StringRedisTemplate 前显式构造键。 */
@Component
public class RedisKeyUtil {

    private final RedisKeyProperties redisKeyProperties;

    /**
     * 装配当前环境的 Redis 键前缀配置。
     *
     * @param redisKeyProperties 开发或测试环境的键命名空间配置
     */
    public RedisKeyUtil(RedisKeyProperties redisKeyProperties) {
        this.redisKeyProperties = redisKeyProperties;
    }

    /**
     * 生成完整 Redis 键，不读取或修改 Redis 数据。
     *
     * @param businessKey 不含项目前缀的业务键，例如 user:123；不能为 null 或空白
     * @return 当前环境前缀加业务键
     * @throws IllegalArgumentException 业务键为 null 或空白
     */
    public String build(String businessKey) {
        if (!StringUtils.hasText(businessKey)) {
            throw new IllegalArgumentException("Redis 业务键不能为空");
        }
        return redisKeyProperties.getKeyPrefix() + businessKey;
    }
}

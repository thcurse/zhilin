package com.zhilin.mapper.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhilin.entity.auth.UserAccountEntity;

/** 登录账号持久化，用户名唯一性由数据库索引保证。 */
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {
}

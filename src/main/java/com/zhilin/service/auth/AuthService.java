package com.zhilin.service.auth;

import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.auth.AuthTokenPairDTO;
import com.zhilin.dto.auth.PasswordLoginDTO;
import com.zhilin.dto.auth.UserRegisterDTO;
import com.zhilin.vo.auth.CurrentUserVO;

/** 账号注册、认证与会话生命周期；角色及账号状态以数据库为准。 */
public interface AuthService {
    /**
     * 创建普通账号，不自动登录；由数据库唯一索引保证并发注册不产生同名账号。
     *
     * @param userRegisterDTO 已通过入口校验的注册参数，包含登录名和原始密码
     * @return 新建普通账号的公开信息，不包含密码摘要
     * @throws BusinessException 用户名已被使用
     */
    CurrentUserVO register(UserRegisterDTO userRegisterDTO);

    /**
     * 从显式启用的初始化入口创建管理员，不覆盖已有账号或提升其权限。
     *
     * @param userRegisterDTO 已通过入口校验的注册参数，包含登录名和原始密码
     * @return 新建管理员的公开信息
     * @throws BusinessException 用户名已被使用，初始化被拒绝
     */
    CurrentUserVO initializeAdmin(UserRegisterDTO userRegisterDTO);

    /**
     * 校验密码与账号权限，创建独立 Redis 会话并签发双令牌。
     *
     * @param passwordLoginDTO 已通过入口校验的用户名和原始密码
     * @param authClientEnum 请求所属端，管理端额外要求 ADMIN 角色
     * @return 访问凭证、用户信息及只供 Cookie 写入的刷新凭证
     * @throws BusinessException 用户名或密码错误、账号已删除或没有管理端权限
     */
    AuthTokenPairDTO login(PasswordLoginDTO passwordLoginDTO, AuthClientEnum authClientEnum);

    /**
     * 轮换刷新令牌并签发新的访问令牌，保留原会话的绝对到期时间。
     * Redis 原子校验旧刷新编号，同一个刷新令牌只能成功使用一次。
     *
     * @param refreshToken 客户端提交的刷新 JWT，不能为空且必须属于请求端
     * @param authClientEnum 请求所属端，用于校验令牌受众和账号权限
     * @return 新的双令牌及各自剩余有效秒数、用户信息
     * @throws BusinessException 令牌无效、会话失效、重复刷新或账号无权限
     */
    AuthTokenPairDTO refresh(String refreshToken, AuthClientEnum authClientEnum);

    /**
     * 校验访问 JWT、Redis 会话及实时账号权限。
     *
     * @param accessToken Bearer 请求头中提取的访问 JWT，不包含 Bearer 前缀
     * @param authClientEnum 接口所属端，用于阻止用户端与管理端凭证混用
     * @return 验证通过的服务端会话声明
     * @throws BusinessException 访问令牌无效、会话已撤销或账号无权限
     */
    AuthSessionDTO authenticate(String accessToken, AuthClientEnum authClientEnum);

    /**
     * 根据会话重新检查账号权限，返回可公开的当前身份。
     *
     * @param authSessionDTO 服务端已认证的会话，包含账号、登录端和会话编号
     * @return 当前账号编号、登录名和角色，不包含密码摘要
     * @throws BusinessException 账号不存在、已逻辑删除或不具备当前端的访问权限
     */
    CurrentUserVO currentUser(AuthSessionDTO authSessionDTO);

    /**
     * 删除当前 Redis 会话，使其访问令牌与刷新令牌失效；其他设备会话不受影响。
     *
     * @param authSessionDTO 服务端已认证的会话，包含账号、登录端和会话编号
     */
    void logout(AuthSessionDTO authSessionDTO);
}

package com.zhilin.service.user;

import com.zhilin.common.exception.BusinessException;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.user.UserProfileUpdateDTO;
import com.zhilin.vo.user.UserProfileVO;

/** 本人资料的读取与修改，不接受客户端传入目标账号编号。 */
public interface UserProfileService {
    /**
     * 读取本人的资料；没有资料记录时返回默认展示值，不进行数据库写入。
     *
     * @param authSessionDTO 当前经过认证的会话，决定资料所属人
     * @return 本人资料，尚未上传时头像地址为 null
     * @throws BusinessException 账号无效或账号、资料已逻辑删除
     */
    UserProfileVO currentProfile(AuthSessionDTO authSessionDTO);

    /**
     * 整体保存本人的文字资料，可选字段为空时清空；头像保持不变。
     *
     * @param authSessionDTO 当前经过认证的会话，决定资料所属人
     * @param userProfileUpdateDTO 已通过校验的文字资料，不包含权限字段
     * @return 保存后的本人资料
     * @throws BusinessException 账号无效或账号、资料已逻辑删除
     */
    UserProfileVO updateProfile(AuthSessionDTO authSessionDTO, UserProfileUpdateDTO userProfileUpdateDTO);

    /**
     * 保存已经上传成功的头像标识，不覆盖现有文字资料。
     *
     * @param authSessionDTO 已认证会话，决定头像所属账号
     * @param avatarKey 上传服务生成的随机 PNG 文件标识，不接受客户端路径
     * @return 已更新头像的本人资料
     * @throws BusinessException 账号、资料无效或头像标识格式错误
     */
    UserProfileVO updateAvatar(AuthSessionDTO authSessionDTO, String avatarKey);
}

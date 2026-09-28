package com.zhilin.service.user.impl;

import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.user.UserProfileUpdateDTO;
import com.zhilin.entity.user.UserProfileEntity;
import com.zhilin.mapper.user.UserProfileMapper;
import com.zhilin.service.auth.AuthService;
import com.zhilin.service.user.UserProfileService;
import com.zhilin.vo.auth.CurrentUserVO;
import com.zhilin.vo.user.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 区分登录账号与展示资料，写入目标始终来自服务端会话。 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileMapper userProfileMapper;
    private final AuthService authService;

    /**
     * 读取本人的资料；尚未保存时使用登录名作为默认昵称，不补写记录。
     *
     * @param authSessionDTO 当前已认证的会话
     * @return 本人可见的资料，缺省文字字段为空字符串
     * @throws BusinessException 账号无效或账号、资料已逻辑删除
     */
    @Override
    public UserProfileVO currentProfile(AuthSessionDTO authSessionDTO) {
        CurrentUserVO currentUserVO = authService.currentUser(authSessionDTO);
        UserProfileEntity userProfileEntity = findActiveProfile(authSessionDTO.userId());
        return toProfileVO(currentUserVO, userProfileEntity);
    }

    /**
     * 保存本人文字资料，数据库按账号主键原子创建或更新，头像不受影响。
     *
     * @param authSessionDTO 当前已认证的会话，目标账号不来自请求体
     * @param userProfileUpdateDTO 已通过入口校验的完整文字资料
     * @return 保存后的本人资料
     * @throws BusinessException 账号无效或账号、资料已逻辑删除
     */
    @Override
    @Transactional
    public UserProfileVO updateProfile(AuthSessionDTO authSessionDTO, UserProfileUpdateDTO userProfileUpdateDTO) {
        CurrentUserVO currentUserVO = authService.currentUser(authSessionDTO);
        findActiveProfile(authSessionDTO.userId());
        UserProfileEntity userProfileEntity = new UserProfileEntity();
        userProfileEntity.setUserId(authSessionDTO.userId());
        userProfileEntity.setNickname(userProfileUpdateDTO.getNickname().strip());
        userProfileEntity.setBio(normalizeOptionalText(userProfileUpdateDTO.getBio()));
        userProfileEntity.setCompany(normalizeOptionalText(userProfileUpdateDTO.getCompany()));
        userProfileEntity.setPosition(normalizeOptionalText(userProfileUpdateDTO.getPosition()));
        userProfileEntity.setEmail(normalizeOptionalText(userProfileUpdateDTO.getEmail()));
        userProfileMapper.saveTextProfile(userProfileEntity);
        return toProfileVO(currentUserVO, findActiveProfile(authSessionDTO.userId()));
    }

    /**
     * 查询指定账号的资料，存在删除记录时拒绝读写，不隐式恢复资料。
     *
     * @param userId 已由当前认证会话确定的账号编号
     * @return 未删除的资料；从未保存时返回 null
     * @throws BusinessException 资料的删除标记不为 0
     */
    private UserProfileEntity findActiveProfile(long userId) {
        UserProfileEntity userProfileEntity = userProfileMapper.selectById(userId);
        if (userProfileEntity != null && !Integer.valueOf(0).equals(userProfileEntity.getDeleted())) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN);
        }
        return userProfileEntity;
    }

    /**
     * 在头像上传成功后保存头像标识，数据库事务不包含对象存储网络请求。
     *
     * @param authSessionDTO 已认证会话，作为唯一的目标身份来源
     * @param avatarKey 服务端生成的 32 位随机编号加 .png 后缀
     * @return 保存后的本人资料，其他文字字段保持原值
     * @throws BusinessException 账号、资料无效或头像标识格式不正确
     */
    @Override
    @Transactional
    public UserProfileVO updateAvatar(AuthSessionDTO authSessionDTO, String avatarKey) {
        if (avatarKey == null || !avatarKey.matches("[a-f0-9]{32}\\.png")) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST);
        }
        CurrentUserVO currentUserVO = authService.currentUser(authSessionDTO);
        findActiveProfile(authSessionDTO.userId());
        userProfileMapper.saveAvatar(authSessionDTO.userId(), currentUserVO.username(), avatarKey);
        return toProfileVO(currentUserVO, findActiveProfile(authSessionDTO.userId()));
    }

    /**
     * 合并只读登录身份与可编辑资料，不返回头像磁盘路径等内部信息。
     *
     * @param currentUserVO 已确认有效的当前登录账号
     * @param userProfileEntity 本人资料，首次保存前允许为 null
     * @return 仅本人可见的资料展示对象
     */
    private UserProfileVO toProfileVO(CurrentUserVO currentUserVO, UserProfileEntity userProfileEntity) {
        if (userProfileEntity == null) {
            return new UserProfileVO(currentUserVO.id(), currentUserVO.username(), currentUserVO.username(),
                    "", "", "", "", null);
        }
        String avatarUrl = userProfileEntity.getAvatarKey() == null ? null
                : "/api/avatars/" + userProfileEntity.getAvatarKey();
        return new UserProfileVO(currentUserVO.id(), currentUserVO.username(), userProfileEntity.getNickname(),
                userProfileEntity.getBio(), userProfileEntity.getCompany(), userProfileEntity.getPosition(),
                userProfileEntity.getEmail(), avatarUrl);
    }

    /**
     * 统一可选文字的保存方式，省略值与 null 均按清空处理。
     *
     * @param text 可选字段的原始值，允许为 null
     * @return 去除首尾空白的文字，null 转为空字符串
     */
    private String normalizeOptionalText(String text) {
        return text == null ? "" : text.strip();
    }
}

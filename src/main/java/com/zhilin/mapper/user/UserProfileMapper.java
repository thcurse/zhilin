package com.zhilin.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhilin.entity.user.UserProfileEntity;
import org.apache.ibatis.annotations.Param;

/** 个人资料数据访问，以账号编号保证一人一份资料。 */
public interface UserProfileMapper extends BaseMapper<UserProfileEntity> {
    /**
     * 原子创建或替换文字资料，避免首次并发保存产生重复记录；不覆盖头像与删除标记。
     *
     * @param userProfileEntity 从会话确定所属账号的文字资料
     * @return MySQL 报告的影响行数，未变化时允许为 0
     */
    int saveTextProfile(@Param("userProfileEntity") UserProfileEntity userProfileEntity);

    /**
     * 原子保存头像标识，首次上传时使用登录名作为初始昵称，已有文字资料保持不变。
     *
     * @param userId 由当前会话确定的账号编号
     * @param nickname 没有资料时采用的默认昵称
     * @param avatarKey 服务端生成的头像存储标识
     * @return MySQL 报告的影响行数
     */
    int saveAvatar(@Param("userId") long userId, @Param("nickname") String nickname,
                   @Param("avatarKey") String avatarKey);
}

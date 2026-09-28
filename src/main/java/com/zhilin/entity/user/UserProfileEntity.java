package com.zhilin.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 用户可编辑的个人资料，不保存登录密码或账号权限。 */
@Getter
@Setter
@TableName("user_profile")
public class UserProfileEntity {
    /** 所属账号编号，同一个账号最多一条资料记录。 */
    @TableId(type = IdType.INPUT)
    private Long userId;
    /** 社区展示昵称，与不可在此修改的登录名分开。 */
    private String nickname;
    /** 纯文本个人简介，允许空字符串。 */
    private String bio;
    /** 公司名称，允许空字符串。 */
    private String company;
    /** 职位名称，允许空字符串。 */
    private String position;
    /** 仅本人可见的联系邮箱，尚未验证，不作为认证凭据。 */
    private String email;
    /** 服务端生成的头像存储标识，没有上传时为 null。 */
    private String avatarKey;
    /** 逻辑删除标记：0 正常，1 已删除。 */
    private Integer deleted;
}

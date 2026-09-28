package com.zhilin.service.user;

import com.zhilin.common.exception.BusinessException;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.vo.user.UserProfileVO;
import org.springframework.web.multipart.MultipartFile;

/** 校验头像图片、保存对象并更新本人资料，读取时只允许服务端生成的头像键。 */
public interface UserAvatarService {
    /**
     * 上传本人的 PNG/JPEG 头像，校验后缩放到最长边 512 像素并统一保存为 PNG。
     *
     * @param authSessionDTO 当前已认证的会话
     * @param avatarFile 不超过 2MB、宽高均不超过 2048 像素的 PNG/JPEG 文件
     * @return 头像更新后的本人资料
     * @throws BusinessException 账号无效、图片不合法、文件过大或对象存储不可用
     */
    UserProfileVO uploadAvatar(AuthSessionDTO authSessionDTO, MultipartFile avatarFile);

    /**
     * 读取可公开展示的头像，不返回存储凭据或允许任意对象键访问。
     *
     * @param avatarKey 服务端生成的随机 PNG 文件标识
     * @return 头像 PNG 字节
     * @throws BusinessException 头像标识非法、对象不存在或对象存储不可用
     */
    byte[] readAvatar(String avatarKey);
}

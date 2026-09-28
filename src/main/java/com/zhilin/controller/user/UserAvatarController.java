package com.zhilin.controller.user;

import com.zhilin.common.exception.BusinessException;
import com.zhilin.common.response.Result;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.security.AuthContext;
import com.zhilin.service.user.UserAvatarService;
import com.zhilin.vo.user.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 本人头像上传及公开图片读取，文件响应不套统一 JSON 包装。 */
@RestController
@RequiredArgsConstructor
@Tag(name = "个人头像")
public class UserAvatarController {
    private final UserAvatarService userAvatarService;

    /**
     * 上传并直接设置本人头像，头像所属人只取当前认证会话。
     *
     * @param avatarFile multipart 文件字段 file，最多 2MB 的 PNG/JPEG
     * @return 更新头像后的本人资料
     * @throws BusinessException 未登录、图片不合规、账号无效或对象存储不可用
     */
    @PostMapping(value = "/api/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传本人头像", security = @SecurityRequirement(name = "bearerAuth"))
    public Result<UserProfileVO> uploadAvatar(@RequestPart("file") MultipartFile avatarFile) {
        AuthSessionDTO authSessionDTO = AuthContext.getCurrentSession();
        return Result.success(userAvatarService.uploadAvatar(authSessionDTO, avatarFile));
    }

    /**
     * 返回可公开展示的头像内容，避免将存储凭据或内部地址交给浏览器。
     *
     * @param avatarKey 服务端生成的随机 PNG 文件标识，只允许单一路径末尾参数
     * @return PNG 图片响应，带禁止内容类型嗅探的响应头
     * @throws BusinessException 标识错误、对象不存在或对象存储不可用
     */
    @GetMapping("/api/avatars/{avatarKey}")
    @Operation(summary = "查看公开头像")
    public ResponseEntity<byte[]> readAvatar(@PathVariable String avatarKey) {
        byte[] avatarContent = userAvatarService.readAvatar(avatarKey);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff").body(avatarContent);
    }
}

package com.zhilin.controller.user;

import com.zhilin.common.exception.BusinessException;
import com.zhilin.common.response.Result;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.user.UserProfileUpdateDTO;
import com.zhilin.security.AuthContext;
import com.zhilin.service.user.UserProfileService;
import com.zhilin.vo.user.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人资料入口，客户端无需也不能指定修改目标用户。 */
@RestController
@RequestMapping("/api/users/me/profile")
@RequiredArgsConstructor
@Tag(name = "个人资料")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {
    private final UserProfileService userProfileService;

    /**
     * 获取本人的资料，未保存时返回默认展示值，不在查询中写入数据库。
     *
     * @return 本人资料，邮箱不通过访客接口公开
     * @throws BusinessException 未登录、账号无效或账号、资料已删除
     */
    @GetMapping
    @Operation(summary = "获取本人资料")
    public Result<UserProfileVO> currentProfile() {
        AuthSessionDTO authSessionDTO = AuthContext.getCurrentSession();
        return Result.success(userProfileService.currentProfile(authSessionDTO));
    }

    /**
     * 整体替换本人文字资料，不允许修改登录名、角色和他人资料。
     *
     * @param userProfileUpdateDTO 经过校验的文字资料，可选字段为空时清空
     * @return 保存后的本人资料
     * @throws BusinessException 未登录、账号无效或账号、资料已删除
     */
    @PutMapping
    @Operation(summary = "保存本人文字资料", description = "昵称必填；可选字段省略或为 null 时清空，头像保持不变。")
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UserProfileUpdateDTO userProfileUpdateDTO) {
        AuthSessionDTO authSessionDTO = AuthContext.getCurrentSession();
        return Result.success(userProfileService.updateProfile(authSessionDTO, userProfileUpdateDTO));
    }
}

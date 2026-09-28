package com.zhilin.vo.user;

/**
 * 仅向本人返回的资料，包含尚未验证的联系邮箱，不用于访客公开主页。
 *
 * @param userId 字符串形式的账号编号
 * @param username 只读登录名，修改昵称不会修改登录名
 * @param nickname 社区展示昵称；尚未保存资料时使用登录名
 * @param bio 个人简介，空字符串表示未填写
 * @param company 公司名称，空字符串表示未填写
 * @param position 职位名称，空字符串表示未填写
 * @param email 本人联系邮箱，不作为已验证身份
 * @param avatarUrl 头像访问地址；未上传时为 null
 */
public record UserProfileVO(String userId, String username, String nickname, String bio,
                            String company, String position, String email, String avatarUrl) {
}

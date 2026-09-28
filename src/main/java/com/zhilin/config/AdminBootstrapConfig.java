package com.zhilin.config;

import com.zhilin.dto.auth.UserRegisterDTO;
import com.zhilin.service.auth.AuthService;
import com.zhilin.vo.auth.CurrentUserVO;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 显式启用的一次性管理员初始化，默认不执行，不提供公开 HTTP 入口。 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "zhilin.bootstrap.admin-enabled", havingValue = "true")
public class AdminBootstrapConfig {
    /**
     * 创建仅在显式启用初始化开关时执行的管理员初始化任务。
     *
     * @param authService 负责创建管理员且拒绝覆盖同名账号的认证服务
     * @param validator 验证初始化参数是否满足注册规则
     * @param username 通过 ADMIN_USERNAME 提供的管理员登录名
     * @param password 通过 ADMIN_PASSWORD 提供的原始密码，不记录到日志
     * @return 应用启动后执行的任务；参数不合法或账号冲突时任务失败
     */
    @Bean
    public ApplicationRunner initializeAdmin(AuthService authService, Validator validator,
            @Value("${ADMIN_USERNAME:}") String username, @Value("${ADMIN_PASSWORD:}") String password) {
        return arguments -> {
            UserRegisterDTO userRegisterDTO = new UserRegisterDTO();
            userRegisterDTO.setUsername(username);
            userRegisterDTO.setPassword(password);
            if (!validator.validate(userRegisterDTO).isEmpty()) {
                throw new IllegalArgumentException("管理员初始化参数不符合注册规则，请检查 ADMIN_USERNAME 和 ADMIN_PASSWORD");
            }
            CurrentUserVO adminCurrentUserVO = authService.initializeAdmin(userRegisterDTO);
            log.info("管理员初始化完成，账号编号：{}。请关闭初始化开关并移除初始化凭据。", adminCurrentUserVO.id());
        };
    }
}

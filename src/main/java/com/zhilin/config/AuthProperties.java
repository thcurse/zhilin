package com.zhilin.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** JWT 和浏览器凭证配置；签名密钥必须由环境或本机忽略文件提供。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("zhilin.auth")
public class AuthProperties {
    /** 至少 32 个字符的高熵密钥，不提供内置默认值。 */
    @NotBlank
    @Size(min = 32)
    private String secret;
    /** 用于校验令牌来源的签发者名称。 */
    @NotBlank
    private String issuer = "zhilin";
    /** 访问令牌有效秒数，默认 15 分钟。 */
    @Min(1)
    private long accessSeconds = 900;
    /** 会话有效秒数，默认 7 天，刷新不会延长绝对截止时间。 */
    @Min(1)
    private long refreshSeconds = 604800;
    /** HTTPS 部署保持 true；本机 HTTP 仅在 dev 配置中覆盖。 */
    private boolean cookieSecure = true;
    /** 允许发起浏览器认证请求的完整 Origin；保留 allowed-origins 配置名称。HTTPS 部署显式配置实际前端地址。 */
    private java.util.List<String> allowedOrigins = java.util.List.of();
}

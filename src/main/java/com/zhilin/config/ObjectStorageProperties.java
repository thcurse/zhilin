package com.zhilin.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** S3 对象存储连接配置，密钥仅从环境变量或忽略的本机配置读取。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("zhilin.storage")
public class ObjectStorageProperties {
    /** S3 API 地址；本机服务仅监听回环接口。 */
    @NotBlank
    private String endpoint = "http://127.0.0.1:18333";
    /** S3 签名区域，本机兼容服务使用 us-east-1。 */
    @NotBlank
    private String region = "us-east-1";
    /** 当前环境的私有桶名称，测试必须使用独立桶。 */
    @NotBlank
    private String bucket = "zhilin-media";
    /** S3 访问标识，空值时上传和读取返回服务不可用，不启用匿名访问。 */
    private String accessKey = "";
    /** S3 签名密钥，禁止写入日志或返回前端。 */
    private String secretKey = "";
}

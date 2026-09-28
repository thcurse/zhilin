package com.zhilin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

/** 装配 S3 客户端，使用路径形式访问桶并限制网络等待时间。 */
@Configuration
public class ObjectStorageConfig {
    /**
     * 创建可复用且随应用关闭的 S3 客户端，不在启动时写桶或探测网络。
     *
     * @param objectStorageProperties 当前环境的 S3 地址、桶及凭据
     * @return 使用显式凭据、路径式寻址和请求超时的 S3 客户端
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(ObjectStorageProperties objectStorageProperties) {
        return S3Client.builder()
                .endpointOverride(URI.create(objectStorageProperties.getEndpoint()))
                .region(Region.of(objectStorageProperties.getRegion()))
                .forcePathStyle(true)
                .credentialsProvider(() -> AwsBasicCredentials.create(
                        objectStorageProperties.getAccessKey(), objectStorageProperties.getSecretKey()))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(builder -> builder.apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();
    }
}

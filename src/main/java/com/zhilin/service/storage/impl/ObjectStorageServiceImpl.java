package com.zhilin.service.storage.impl;

import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.config.ObjectStorageProperties;
import com.zhilin.service.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 文件访问；上传失败不会返回假链接，对象缺失与存储故障分别处理。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorageServiceImpl implements ObjectStorageService {
    private final S3Client s3Client;
    private final ObjectStorageProperties objectStorageProperties;

    /**
     * 将已校验的内容保存到配置的私有桶，不开放匿名写入。
     *
     * @param objectKey 服务端生成的完整对象键
     * @param content 业务层完成格式和大小校验的字节内容
     * @param contentType 已确定的媒体类型
     * @throws BusinessException 凭据缺失或 S3 写入失败
     */
    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        requireCredentials();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(objectStorageProperties.getBucket()).key(objectKey).contentType(contentType).build();
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            log.error("对象存储写入失败，对象键：{}", objectKey, exception);
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 读取受业务层大小限制的头像；存储异常只向客户端返回安全提示。
     *
     * @param objectKey 已通过业务校验的对象键
     * @return 对象内容字节
     * @throws BusinessException 对象不存在或存储不可用
     */
    @Override
    public byte[] readObject(String objectKey) {
        requireCredentials();
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(objectStorageProperties.getBucket()).key(objectKey).build();
            return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND);
        } catch (SdkException exception) {
            log.error("对象存储读取失败，对象键：{}", objectKey, exception);
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 使用前检查凭据，允许不涉及存储的启动测试独立运行，但不进行匿名回退。
     *
     * @throws BusinessException 对象存储访问标识或签名密钥未配置
     */
    private void requireCredentials() {
        if (objectStorageProperties.getAccessKey().isBlank() || objectStorageProperties.getSecretKey().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE);
        }
    }
}

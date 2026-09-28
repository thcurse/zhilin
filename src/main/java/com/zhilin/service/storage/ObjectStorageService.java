package com.zhilin.service.storage;

import com.zhilin.common.exception.BusinessException;

/** 通过 S3 存取业务文件，调用方负责校验内容和生成对象键。 */
public interface ObjectStorageService {
    /**
     * 将已验证内容写入当前环境的私有桶。
     *
     * @param objectKey 服务端生成的对象键，不是本机文件路径
     * @param content 已验证且受大小限制的文件内容
     * @param contentType 对象的确定媒体类型，不直接信任上传请求头
     * @throws BusinessException 凭据未配置或对象存储写入失败
     */
    void putObject(String objectKey, byte[] content, String contentType);

    /**
     * 读取当前环境私有桶中的对象；仅用于本阶段受大小限制的头像。
     *
     * @param objectKey 已由业务层验证的对象键
     * @return 对象内容字节
     * @throws BusinessException 对象不存在或对象存储不可用
     */
    byte[] readObject(String objectKey);
}

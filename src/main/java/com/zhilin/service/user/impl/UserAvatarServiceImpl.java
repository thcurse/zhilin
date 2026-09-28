package com.zhilin.service.user.impl;

import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.service.storage.ObjectStorageService;
import com.zhilin.service.user.UserAvatarService;
import com.zhilin.service.user.UserProfileService;
import com.zhilin.vo.user.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;

/** 头像只按实际图片内容校验，不信任扩展名、请求 MIME 类型或客户端文件路径。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {
    /** 上传文件字节上限，与 HTTP multipart 配置一致。 */
    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    /** 在解码像素前限制输入宽高，避免小文件展开成过大的图片。 */
    private static final int MAX_INPUT_DIMENSION = 2048;
    /** 存储图最长边，保持原比例且不放大小图片。 */
    private static final int MAX_OUTPUT_DIMENSION = 512;
    private final ObjectStorageService objectStorageService;
    private final UserProfileService userProfileService;

    /**
     * 校验当前用户与图片，先保存对象再更新资料，失败时不返回成功链接。
     *
     * @param authSessionDTO 已认证会话，决定头像所属账号
     * @param avatarFile 最多 2MB 的 PNG/JPEG 上传文件
     * @return 已保存头像地址的本人资料
     * @throws BusinessException 账号或图片无效、图片过大或存储不可用
     */
    @Override
    public UserProfileVO uploadAvatar(AuthSessionDTO authSessionDTO, MultipartFile avatarFile) {
        userProfileService.currentProfile(authSessionDTO);
        byte[] avatarContent = normalizeAvatar(avatarFile);
        // 随机键不包含原文件名和用户输入，旧头像链接不会被并发上传覆盖。
        String avatarKey = UUID.randomUUID().toString().replace("-", "") + ".png";
        objectStorageService.putObject("avatars/" + avatarKey, avatarContent, "image/png");
        try {
            return userProfileService.updateAvatar(authSessionDTO, avatarKey);
        } catch (RuntimeException exception) {
            // 跨存储没有共同事务；保留对象便于排查，避免提交结果不确定时误删已引用头像。
            log.error("头像对象已上传但资料保存失败，待核对对象键：avatars/{}", avatarKey, exception);
            throw exception;
        }
    }

    /**
     * 读取头像专属前缀下的随机 PNG，拒绝路径片段或其他桶内对象名称。
     *
     * @param avatarKey 服务端生成的 32 位随机编号加 .png 后缀
     * @return 可公开展示的 PNG 内容
     * @throws BusinessException 标识非法、文件不存在或对象存储不可用
     */
    @Override
    public byte[] readAvatar(String avatarKey) {
        if (avatarKey == null || !avatarKey.matches("[a-f0-9]{32}\\.png")) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND);
        }
        return objectStorageService.readObject("avatars/" + avatarKey);
    }

    /**
     * 限制读取字节数，按真实格式解码并重新编码，避免将 HTML、SVG 或元数据原样公开。
     *
     * @param avatarFile 原始上传文件，不信任其文件名和声明的媒体类型
     * @return 去除原始元数据、限制尺寸后的 PNG 字节
     * @throws BusinessException 文件为空、过大、格式错误或无法解码
     */
    private byte[] normalizeAvatar(MultipartFile avatarFile) {
        if (avatarFile.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.INVALID_AVATAR);
        }
        if (avatarFile.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException(ErrorCodeEnum.PAYLOAD_TOO_LARGE);
        }
        try (InputStream inputStream = avatarFile.getInputStream()) {
            byte[] originalContent = inputStream.readNBytes(MAX_AVATAR_BYTES + 1);
            if (originalContent.length > MAX_AVATAR_BYTES) {
                throw new BusinessException(ErrorCodeEnum.PAYLOAD_TOO_LARGE);
            }
            return decodeAndResize(originalContent);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_AVATAR);
        }
    }

    /**
     * 先读取图片头限制格式和像素尺寸，再解码缩放，避免直接加载超大像素矩阵。
     *
     * @param originalContent 已通过字节数限制的原始图片内容
     * @return 最长边不超过 512 像素的 PNG 内容
     * @throws IOException 图片读取或编码失败
     * @throws BusinessException 不是 PNG/JPEG 或输入尺寸超限
     */
    private byte[] decodeAndResize(byte[] originalContent) throws IOException {
        try (MemoryCacheImageInputStream imageInputStream = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(originalContent))) {
            Iterator<ImageReader> imageReaderIterator = ImageIO.getImageReaders(imageInputStream);
            if (!imageReaderIterator.hasNext()) {
                throw new BusinessException(ErrorCodeEnum.INVALID_AVATAR);
            }
            ImageReader imageReader = imageReaderIterator.next();
            try {
                imageReader.setInput(imageInputStream, true, true);
                String format = imageReader.getFormatName();
                int width = imageReader.getWidth(0);
                int height = imageReader.getHeight(0);
                if (!("PNG".equalsIgnoreCase(format) || "JPEG".equalsIgnoreCase(format))
                        || width < 1 || height < 1 || width > MAX_INPUT_DIMENSION || height > MAX_INPUT_DIMENSION) {
                    throw new BusinessException(ErrorCodeEnum.INVALID_AVATAR);
                }
                BufferedImage originalImage = imageReader.read(0);
                return encodeResizedPng(originalImage);
            } finally {
                imageReader.dispose();
            }
        }
    }

    /**
     * 保持宽高比缩小图片，使用新的像素缓冲区重新编码，保留透明背景。
     *
     * @param originalImage 已通过尺寸校验的原始图片
     * @return 重新编码后的 PNG 字节
     * @throws IOException 当前运行环境无法编码 PNG
     */
    private byte[] encodeResizedPng(BufferedImage originalImage) throws IOException {
        // 缩放比例最高为 1，不放大小图片，最长边固定不超过输出上限。
        double scale = Math.min(1.0, (double) MAX_OUTPUT_DIMENSION
                / Math.max(originalImage.getWidth(), originalImage.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(originalImage.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(originalImage.getHeight() * scale));
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (!ImageIO.write(resizedImage, "PNG", outputStream)) {
            throw new IOException("当前环境没有 PNG 编码器");
        }
        return outputStream.toByteArray();
    }
}

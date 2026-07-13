package com.wzh.blog.util;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.exception.BizException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * 文件md5工具类
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Log4j2
public class FileUtils {

    private static final long MAX_IMAGE_PIXELS = 40_000_000L;

    public static void validateUpload(MultipartFile file, String path) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String extension = getExtName(file.getOriginalFilename()).toLowerCase();
        if (FilePathEnum.VOICE.getPath().equals(path)) {
            if (!".wav".equals(extension) || !isWaveFile(file)) {
                throw new BizException("仅支持 WAV 格式的语音文件");
            }
            return;
        }
        if (!(".jpg".equals(extension) || ".jpeg".equals(extension) || ".png".equals(extension))) {
            throw new BizException("仅支持 JPG、JPEG 或 PNG 图片");
        }
        validateImage(file);
    }

    private static void validateImage(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                    || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
                throw new BizException("图片格式或尺寸不合法");
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("无法读取图片文件");
        }
    }

    private static boolean isWaveFile(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(12);
            return header.length == 12
                    && "RIFF".equals(new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                    && "WAVE".equals(new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取文件md5值
     *
     * @param inputStream 文件输入流
     * @return {@link String} 文件md5值
     */
    public static String getMd5(InputStream inputStream) {
        try (InputStream stream = inputStream) {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                md5.update(buffer, 0, length);
            }
            return new String(Hex.encodeHex(md5.digest()));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate file checksum", e);
        }
    }

    /**
     * 得到文件扩展名
     *
     * @param fileName 文件名称
     * @return {@link String} 文件后缀
     */
    public static String getExtName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * 转换file
     *
     * @param multipartFile 多部分文件
     * @return {@link File} file
     */
    public static File multipartFileToFile(MultipartFile multipartFile) {
        try {
            String originalFilename = multipartFile.getOriginalFilename();
            String[] filename = Objects.requireNonNull(originalFilename).split("\\.");
            File file = File.createTempFile(filename[0], filename[1]);
            multipartFile.transferTo(file);
            file.deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create temporary upload file", e);
        }
    }


    /**
     * 自动调节精度(经验数值)
     *
     * @param size 源图片大小
     * @return 图片压缩质量比
     */
    private static double getAccuracy(long size) {
        double accuracy;
        if (size < 900) {
            accuracy = 0.85;
        } else if (size < 2048) {
            accuracy = 0.6;
        } else if (size < 3072) {
            accuracy = 0.44;
        } else {
            accuracy = 0.4;
        }

        return accuracy;
    }

}

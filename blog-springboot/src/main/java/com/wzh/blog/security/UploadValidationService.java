package com.wzh.blog.security;

import com.wzh.blog.content.MarkdownSanitizer;
import com.wzh.blog.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** Validates the image and Markdown inputs used by the retained editor. */
@Service
public class UploadValidationService {

    private static final long DEFAULT_MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int DEFAULT_MAX_IMAGE_WIDTH = 8_000;
    private static final int DEFAULT_MAX_IMAGE_HEIGHT = 8_000;
    private static final long DEFAULT_MAX_IMAGE_PIXELS = 40_000_000L;

    private final MarkdownSanitizer markdownSanitizer;
    private final long maxImageBytes;
    private final int maxImageWidth;
    private final int maxImageHeight;
    private final long maxImagePixels;

    @Autowired
    public UploadValidationService(MarkdownSanitizer markdownSanitizer) {
        this(markdownSanitizer, DEFAULT_MAX_IMAGE_BYTES, DEFAULT_MAX_IMAGE_WIDTH,
                DEFAULT_MAX_IMAGE_HEIGHT, DEFAULT_MAX_IMAGE_PIXELS);
    }

    public UploadValidationService(MarkdownSanitizer markdownSanitizer,
                                   long maxImageBytes,
                                   int maxImageWidth,
                                   int maxImageHeight,
                                   long maxImagePixels) {
        this.markdownSanitizer = markdownSanitizer;
        this.maxImageBytes = maxImageBytes;
        this.maxImageWidth = maxImageWidth;
        this.maxImageHeight = maxImageHeight;
        this.maxImagePixels = maxImagePixels;
    }

    public void validate(MultipartFile file, String ignoredPath) {
        validateImage(file);
    }

    public void validateImage(MultipartFile file) {
        byte[] bytes = read(file, maxImageBytes);
        String extension = extension(file).toLowerCase(Locale.ROOT);
        if (!".jpg".equals(extension) && !".jpeg".equals(extension) && !".png".equals(extension)) {
            throw new BizException("仅支持 JPG、JPEG 或 PNG 图片");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > maxImageWidth || image.getHeight() > maxImageHeight
                    || (long) image.getWidth() * image.getHeight() > maxImagePixels) {
                throw new BizException("图片格式或尺寸不合法");
            }
        } catch (IOException exception) {
            throw new BizException("无法读取图片文件");
        }
    }

    public String validateMarkdown(String markdown) {
        return markdownSanitizer.sanitize(markdown);
    }

    public String extension(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            throw new BizException("文件名不能为空");
        }
        String filename = file.getOriginalFilename();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BizException("文件扩展名不合法");
        }
        return filename.substring(dot);
    }

    private byte[] read(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片文件");
        }
        if (file.getSize() > maxBytes) {
            throw new BizException("图片文件过大");
        }
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(Math.toIntExact(Math.min(Integer.MAX_VALUE, maxBytes + 1)));
            if (bytes.length > maxBytes || input.read() != -1) {
                throw new BizException("图片文件过大");
            }
            return bytes;
        } catch (IOException exception) {
            throw new BizException("无法读取图片文件");
        }
    }
}

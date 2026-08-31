package com.wzh.blog.security;

import com.wzh.blog.content.MarkdownSanitizer;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Byte- and decoder-based validation shared by every multipart endpoint. */
@Service
public class UploadValidationService {

    private static final long DEFAULT_MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long DEFAULT_MAX_AUDIO_BYTES = 20L * 1024 * 1024;
    private static final int DEFAULT_MAX_IMAGE_WIDTH = 8_000;
    private static final int DEFAULT_MAX_IMAGE_HEIGHT = 8_000;
    private static final long DEFAULT_MAX_IMAGE_PIXELS = 40_000_000L;
    private static final int DEFAULT_MAX_AUDIO_SAMPLE_RATE = 48_000;
    private static final int DEFAULT_MAX_AUDIO_CHANNELS = 2;
    private static final int DEFAULT_MAX_AUDIO_DURATION_SECONDS = 600;

    private final MarkdownSanitizer markdownSanitizer;
    private final long maxImageBytes;
    private final long maxAudioBytes;
    private final int maxImageWidth;
    private final int maxImageHeight;
    private final long maxImagePixels;
    private final int maxAudioSampleRate;
    private final int maxAudioChannels;
    private final int maxAudioDurationSeconds;

    @Autowired
    public UploadValidationService(MarkdownSanitizer markdownSanitizer) {
        this(markdownSanitizer, DEFAULT_MAX_IMAGE_BYTES, DEFAULT_MAX_AUDIO_BYTES,
                DEFAULT_MAX_IMAGE_WIDTH, DEFAULT_MAX_IMAGE_HEIGHT, DEFAULT_MAX_IMAGE_PIXELS,
                DEFAULT_MAX_AUDIO_SAMPLE_RATE, DEFAULT_MAX_AUDIO_CHANNELS,
                DEFAULT_MAX_AUDIO_DURATION_SECONDS);
    }

    public UploadValidationService(MarkdownSanitizer markdownSanitizer,
                                   long maxImageBytes,
                                   long maxAudioBytes,
                                   int maxImageWidth,
                                   int maxImageHeight,
                                   long maxImagePixels,
                                   int maxAudioSampleRate,
                                   int maxAudioChannels,
                                   int maxAudioDurationSeconds) {
        this.markdownSanitizer = markdownSanitizer;
        this.maxImageBytes = maxImageBytes;
        this.maxAudioBytes = maxAudioBytes;
        this.maxImageWidth = maxImageWidth;
        this.maxImageHeight = maxImageHeight;
        this.maxImagePixels = maxImagePixels;
        this.maxAudioSampleRate = maxAudioSampleRate;
        this.maxAudioChannels = maxAudioChannels;
        this.maxAudioDurationSeconds = maxAudioDurationSeconds;
    }

    public void validate(MultipartFile file, String path) {
        if (FilePathEnum.VOICE.getPath().equals(path)) {
            validateAudio(file);
        } else {
            validateImage(file);
        }
    }

    public void validateImage(MultipartFile file) {
        byte[] bytes = read(file, maxImageBytes, "图片");
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

    public void validateAudio(MultipartFile file) {
        byte[] bytes = read(file, maxAudioBytes, "音频");
        if (!".wav".equals(extension(file).toLowerCase(Locale.ROOT))) {
            throw new BizException("仅支持 WAV 格式的语音文件");
        }
        if (bytes.length < 12 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) {
            throw new BizException("语音文件格式不合法");
        }
        int offset = 12;
        int channels = -1;
        long sampleRate = -1;
        int bitsPerSample = -1;
        long dataBytes = -1;
        while (offset + 8 <= bytes.length) {
            String chunk = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = littleEndianUnsignedInt(bytes, offset + 4);
            long end = offset + 8L + chunkSize;
            if (end > bytes.length || end < offset) {
                throw new BizException("语音文件块长度不合法");
            }
            if ("fmt ".equals(chunk) && chunkSize >= 16) {
                int format = littleEndianUnsignedShort(bytes, offset + 8);
                channels = littleEndianUnsignedShort(bytes, offset + 10);
                sampleRate = littleEndianUnsignedInt(bytes, offset + 12);
                bitsPerSample = littleEndianUnsignedShort(bytes, offset + 22);
                if (format != 1) {
                    throw new BizException("仅支持 PCM WAV 音频");
                }
            } else if ("data".equals(chunk)) {
                dataBytes = chunkSize;
            }
            offset = (int) end + ((chunkSize & 1) == 1 ? 1 : 0);
        }
        if (channels < 1 || channels > maxAudioChannels || sampleRate < 1
                || sampleRate > maxAudioSampleRate || (bitsPerSample != 8 && bitsPerSample != 16)
                || dataBytes < 0) {
            throw new BizException("语音参数不合法");
        }
        long bytesPerSecond = sampleRate * channels * bitsPerSample / 8;
        if (bytesPerSecond <= 0 || dataBytes > bytesPerSecond * maxAudioDurationSeconds) {
            throw new BizException("语音时长超过限制");
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

    private byte[] read(MultipartFile file, long maxBytes, String kind) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的" + kind + "文件");
        }
        if (file.getSize() > maxBytes) {
            throw new BizException(kind + "文件过大");
        }
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(Math.toIntExact(Math.min(Integer.MAX_VALUE, maxBytes + 1)));
            if (bytes.length > maxBytes || input.read() != -1) {
                throw new BizException(kind + "文件过大");
            }
            return bytes;
        } catch (IOException exception) {
            throw new BizException("无法读取" + kind + "文件");
        }
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        return offset + value.length() <= bytes.length
                && value.equals(new String(bytes, offset, value.length(), StandardCharsets.US_ASCII));
    }

    private long littleEndianUnsignedInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
                | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private int littleEndianUnsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }
}

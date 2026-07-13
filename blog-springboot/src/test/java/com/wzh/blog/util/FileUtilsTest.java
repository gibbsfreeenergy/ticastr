package com.wzh.blog.util;

import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileUtilsTest {

    private static final byte[] ONE_PIXEL_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, (byte) 0xc4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xd7, 0x63, (byte) 0xf8, (byte) 0xcf,
            (byte) 0xc0, (byte) 0xf0, 0x1f, 0x00, 0x05, 0x00, 0x01,
            (byte) 0xff, (byte) 0x89, (byte) 0x99, 0x3d, 0x1d, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae,
            0x42, 0x60, (byte) 0x82
    };

    @Test
    void acceptsRecognizedImageContent() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", ONE_PIXEL_PNG);

        assertDoesNotThrow(() -> FileUtils.validateUpload(file, FilePathEnum.AVATAR.getPath()));
    }

    @Test
    void rejectsSpoofedImageExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "not an image".getBytes());

        assertThrows(BizException.class, () -> FileUtils.validateUpload(file, FilePathEnum.AVATAR.getPath()));
    }
}

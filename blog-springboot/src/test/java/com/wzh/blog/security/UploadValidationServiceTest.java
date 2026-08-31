package com.wzh.blog.security;

import com.wzh.blog.content.MarkdownSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadValidationServiceTest {

    @Test
    void rejectsExecutableRenamedAsImage() {
        UploadValidationService service = new UploadValidationService(new MarkdownSanitizer());
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.png", "image/png", "MZ-not-an-image".getBytes(StandardCharsets.US_ASCII));

        assertThrows(RuntimeException.class, () -> service.validateImage(file));
    }

    @Test
    void rejectsDecodedImageAbovePixelLimit() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        UploadValidationService service = new UploadValidationService(
                new MarkdownSanitizer(), 1024 * 1024, 1024 * 1024,
                100, 100, 3, 48_000, 2, 60);
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", output.toByteArray());

        assertThrows(RuntimeException.class, () -> service.validateImage(file));
    }

    @Test
    void rejectsMalformedRiffAudio() {
        UploadValidationService service = new UploadValidationService(new MarkdownSanitizer());
        MockMultipartFile file = new MockMultipartFile(
                "file", "voice.wav", "audio/wav", "RIFF-bad".getBytes(StandardCharsets.US_ASCII));

        assertThrows(RuntimeException.class, () -> service.validateAudio(file));
    }

    @Test
    void rejectsMarkdownAboveUtf8ByteLimit() {
        UploadValidationService service = new UploadValidationService(new MarkdownSanitizer(8));

        assertThrows(RuntimeException.class, () -> service.validateMarkdown("中文中文中文"));
    }
}

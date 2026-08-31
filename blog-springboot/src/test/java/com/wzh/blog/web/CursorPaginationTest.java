package com.wzh.blog.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPaginationTest {

    @Test
    void signsAndRestoresAFeedPosition() {
        CursorCodec codec = new CursorCodec("test-secret", Duration.ofMinutes(5));
        String fingerprint = codec.fingerprint("articles");
        String encoded = codec.encode(LocalDateTime.of(2026, 8, 31, 10, 15), 42, fingerprint);

        CursorCodec.Cursor decoded = codec.decode(encoded, fingerprint);

        assertThat(decoded.id()).isEqualTo(42);
        assertThat(decoded.createTime()).isEqualTo(LocalDateTime.of(2026, 8, 31, 10, 15));
    }

    @Test
    void rejectsTamperingAndWrongFeed() {
        CursorCodec codec = new CursorCodec("test-secret", Duration.ofMinutes(5));
        String fingerprint = codec.fingerprint("articles");
        String encoded = codec.encode(LocalDateTime.of(2026, 8, 31, 10, 15), 42, fingerprint);

        char first = encoded.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';
        String tampered = replacement + encoded.substring(1);
        assertThatThrownBy(() -> codec.decode(tampered, fingerprint))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(encoded, codec.fingerprint("archives")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExpiredCursors() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC);
        CursorCodec codec = new CursorCodec("test-secret", Duration.ofSeconds(1), clock);
        String fingerprint = codec.fingerprint("articles");
        String encoded = codec.encode(LocalDateTime.of(2026, 8, 31, 10, 15), 42, fingerprint);
        CursorCodec expired = new CursorCodec("test-secret", Duration.ofSeconds(1),
                Clock.fixed(Instant.parse("2026-08-31T00:00:02Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> expired.decode(encoded, fingerprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void boundsPageSize() {
        assertThat(CursorPageQuery.of(null, null).size()).isEqualTo(10);
        assertThatThrownBy(() -> CursorPageQuery.of(null, 51))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

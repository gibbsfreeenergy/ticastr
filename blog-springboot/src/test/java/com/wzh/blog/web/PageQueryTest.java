package com.wzh.blog.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void appliesDefaultsAndCalculatesOffsetExplicitly() {
        assertThat(PageQuery.of(null, null)).isEqualTo(new PageQuery(1, 10));
        assertThat(PageQuery.of(3L, 25L).offset()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidSizesAndOffsetOverflow() {
        assertThatThrownBy(() -> PageQuery.of(1L, 101L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(Long.MAX_VALUE, 100).offset())
                .isInstanceOf(IllegalArgumentException.class);
    }
}

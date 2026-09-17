package com.wzh.blog.infrastructure.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseMediaReferenceCheckerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void checksOnlyReferencesSupportedByTheRetainedSchema() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("asset")))
                .thenReturn(false);

        DatabaseMediaReferenceChecker checker = new DatabaseMediaReferenceChecker(jdbcTemplate);

        assertThat(checker.isReferenced("asset")).isFalse();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(5)).queryForObject(queryCaptor.capture(), eq(Boolean.class), eq("asset"));
        List<String> queries = queryCaptor.getAllValues();
        assertThat(queries).noneMatch(query -> query.contains("tb_content_asset"));
    }
}

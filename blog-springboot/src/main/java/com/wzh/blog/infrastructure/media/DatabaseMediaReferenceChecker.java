package com.wzh.blog.infrastructure.media;

import com.wzh.blog.media.MediaReferenceChecker;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** Checks the retained article, page, profile and content references. */
@Service
@Log4j2
public class DatabaseMediaReferenceChecker implements MediaReferenceChecker {

    private static final List<String> REFERENCE_QUERIES = List.of(
            "SELECT EXISTS (SELECT 1 FROM tb_article WHERE article_cover = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_page WHERE page_cover = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_user_info WHERE avatar = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_about WHERE content LIKE CONCAT('%', ?, '%'))",
            "SELECT EXISTS (SELECT 1 FROM tb_website_config WHERE JSON_SEARCH(config, 'one', ?) IS NOT NULL)"
    );

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMediaReferenceChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isReferenced(String fileReference) {
        if (fileReference == null || fileReference.isBlank()) {
            return false;
        }
        try {
            for (String query : REFERENCE_QUERIES) {
                Boolean referenced = jdbcTemplate.queryForObject(query, Boolean.class, fileReference);
                if (Boolean.TRUE.equals(referenced)) {
                    return true;
                }
            }
            return false;
        } catch (DataAccessException exception) {
            log.error("Unable to verify media reference {}; skip deletion", fileReference, exception);
            return true;
        }
    }
}

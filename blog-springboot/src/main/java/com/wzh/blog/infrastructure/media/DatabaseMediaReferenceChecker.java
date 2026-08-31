package com.wzh.blog.infrastructure.media;

import com.wzh.blog.media.MediaReferenceChecker;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Compatibility reference checker for URL-based media columns. New media
 * records should migrate to an asset id; until then deletion fails safe when
 * any known business reference still points at the object.
 */
@Service
@Log4j2
public class DatabaseMediaReferenceChecker implements MediaReferenceChecker {

    private static final List<String> REFERENCE_QUERIES = List.of(
            "SELECT EXISTS (SELECT 1 FROM tb_article WHERE article_cover = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_friend_link WHERE link_avatar = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_page WHERE page_cover = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_photo WHERE photo_src = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_photo_album WHERE album_cover = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_user_info WHERE avatar = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_message WHERE avatar = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_chat_record WHERE type = 5 AND content = ?)",
            "SELECT EXISTS (SELECT 1 FROM tb_talk WHERE images LIKE CONCAT('%', ?, '%'))",
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

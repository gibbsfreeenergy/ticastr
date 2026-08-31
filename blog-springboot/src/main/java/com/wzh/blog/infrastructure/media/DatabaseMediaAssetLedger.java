package com.wzh.blog.infrastructure.media;

import com.wzh.blog.media.MediaAssetLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** MySQL-backed asset ledger used for reconciliation and safe cleanup. */
@Service
@Log4j2
public class DatabaseMediaAssetLedger implements MediaAssetLedger {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMediaAssetLedger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void register(String reference, String objectKey, String storageMode) {
        if (isBlank(reference) || isBlank(objectKey)) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO tb_media_asset
                            (asset_id, asset_reference, object_key, storage_mode, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                        ON DUPLICATE KEY UPDATE
                            asset_reference = ?, object_key = ?, storage_mode = ?, status = 'ACTIVE',
                            updated_at = NOW(), deleted_at = NULL, last_error = NULL
                        """,
                assetId(reference), reference, objectKey, valueOrDefault(storageMode),
                reference, objectKey, valueOrDefault(storageMode));
    }

    @Override
    public void markDeletionStarted(String reference) {
        updateStatus(reference, "DELETING", null);
    }

    @Override
    public void markDeleted(String reference) {
        updateStatus(reference, "DELETED", null);
    }

    @Override
    public void markDeletionFailed(String reference, String reason) {
        updateStatus(reference, "DELETE_FAILED", truncate(reason));
    }

    @Override
    public MediaAssetLocation locationFor(String reference) {
        if (isBlank(reference)) {
            return null;
        }
        try {
            return jdbcTemplate.query("""
                            SELECT storage_mode, object_key
                            FROM tb_media_asset
                            WHERE asset_id = ?
                            """,
                    resultSet -> resultSet.next()
                            ? new MediaAssetLocation(resultSet.getString("storage_mode"),
                            resultSet.getString("object_key"))
                            : null,
                    assetId(reference));
        } catch (RuntimeException exception) {
            log.warn("Unable to resolve media asset location for {}", reference, exception);
            return null;
        }
    }

    @Override
    public List<MediaAssetRecord> listCleanupCandidates(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("""
                        SELECT asset_reference, storage_mode, object_key, status, created_at, updated_at
                        FROM tb_media_asset
                        WHERE status IN ('DELETING', 'DELETE_FAILED')
                          AND updated_at < DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                        ORDER BY updated_at ASC
                        LIMIT ?
                        """,
                (resultSet, rowNum) -> new MediaAssetRecord(
                        resultSet.getString("asset_reference"),
                        resultSet.getString("storage_mode"),
                        resultSet.getString("object_key"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toLocalDateTime(),
                        resultSet.getTimestamp("updated_at").toLocalDateTime()),
                boundedLimit);
    }

    private void updateStatus(String reference, String status, String error) {
        if (isBlank(reference)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                            UPDATE tb_media_asset
                            SET status = ?, last_error = ?,
                                deleted_at = CASE WHEN ? = 'DELETED' THEN NOW() ELSE deleted_at END,
                                updated_at = NOW()
                            WHERE asset_id = ?
                            """,
                    status, error, status, assetId(reference));
        } catch (RuntimeException exception) {
            log.warn("Unable to update media asset ledger for {}", reference, exception);
        }
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }

    private String valueOrDefault(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String assetId(String reference) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(reference.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash media reference", exception);
        }
    }
}

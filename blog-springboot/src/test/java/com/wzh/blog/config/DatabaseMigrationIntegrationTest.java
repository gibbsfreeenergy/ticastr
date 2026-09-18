package com.wzh.blog.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationIntegrationTest {

    private static final List<String> RETAINED_TABLES = List.of(
            "tb_about", "tb_article", "tb_content_asset", "tb_media_asset", "tb_menu",
            "tb_outbox_event", "tb_page", "tb_resource", "tb_role", "tb_role_menu",
            "tb_role_resource", "tb_storage_bootstrap_state", "tb_storage_provider_config",
            "tb_user_auth", "tb_user_info", "tb_user_role", "tb_website_config");

    private static final List<String> REMOVED_TABLES = List.of(
            "tb_article_tag", "tb_category", "tb_tag", "tb_article_like", "tb_article_engagement",
            "tb_comment", "tb_friend_link", "tb_message", "tb_photo", "tb_photo_album",
            "tb_talk", "tb_chat_record", "tb_site_visitor", "tb_unique_view", "tb_operation_log");

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306);

    @Test
    void migratesFreshDatabaseToTheCoreSchema() throws Exception {
        createDatabase("core_blog");
        migrate("core_blog", false);

        try (Connection connection = connection("core_blog")) {
            for (String table : RETAINED_TABLES) {
                assertThat(tableExists(connection, table)).as("retained table %s", table).isTrue();
            }
            for (String table : REMOVED_TABLES) {
                assertThat(tableExists(connection, table)).as("removed table %s", table).isFalse();
            }

            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_menu WHERE parent_id IS NULL")).isEqualTo(8);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_menu")).isEqualTo(10);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_menu WHERE code IN "
                    + "('album', 'category', 'comment', 'friendLink', 'message', 'tag', 'talk', 'user', 'role', 'resource', 'menu')"))
                    .isZero();
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_menu "
                    + "WHERE code = 'article' AND is_hidden = 1 AND parent_id IS NOT NULL")).isEqualTo(1);

            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_role WHERE role_label = 'admin'")).isEqualTo(1);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_role WHERE role_label <> 'admin'")).isZero();
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_resource WHERE parent_id IS NULL")).isEqualTo(2);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_resource "
                    + "WHERE url LIKE '/admin/outbox%' OR url LIKE '/admin/storage/provider%' "
                    + "OR url IN ('/categories', '/tags', '/comments', '/messages', '/links', '/photos/albums', '/talks', '/report')"))
                    .isZero();
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_resource "
                    + "WHERE url = '/admin/pages/*' AND request_method = 'DELETE'"))
                    .isZero();
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_page "
                    + "WHERE page_label IS NULL OR page_label NOT IN ('home', 'archive', 'about')"))
                    .isZero();

            assertThat(columnExists(connection, "tb_article", "category_id")).isFalse();
            assertThat(columnExists(connection, "tb_article", "article_content")).isFalse();
            assertThat(columnExists(connection, "tb_article", "content_asset_id")).isTrue();
            assertThat(columnExists(connection, "tb_user_auth", "ip_address")).isFalse();
            assertThat(columnExists(connection, "tb_user_auth", "ip_source")).isFalse();
            assertThat(columnExists(connection, "tb_user_auth", "last_login_time")).isFalse();
            assertThat(columnExists(connection, "tb_media_asset", "source_type")).isFalse();
            assertThat(queryInt(connection, "SELECT JSON_CONTAINS_PATH(config, 'one', "
                    + "'$.isCommentReview', '$.isMessageReview', '$.isReward', '$.isChatRoom', '$.isMusicPlayer', '$.websocketUrl') "
                    + "FROM tb_website_config WHERE id = 1")).isZero();
        }
    }

    @Test
    void upgradesTheLegacySeedDatabaseAndRemovesRetiredDataStructures() throws Exception {
        createDatabase("legacy_blog");
        try (Connection connection = connection("legacy_blog")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy-schema.sql"));
        }

        migrate("legacy_blog", true);

        try (Connection connection = connection("legacy_blog")) {
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_article WHERE id = 54")).isEqualTo(1);
            assertThat(columnExists(connection, "tb_article", "category_id")).isFalse();
            assertThat(columnExists(connection, "tb_article", "content_asset_id")).isTrue();
            for (String table : REMOVED_TABLES) {
                assertThat(tableExists(connection, table)).as("removed table %s", table).isFalse();
            }
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_page "
                    + "WHERE page_label IS NULL OR page_label NOT IN ('home', 'archive', 'about')"))
                    .isZero();
        }
    }

    private static void migrate(String database, boolean baseline) {
        Flyway.configure()
                .dataSource(jdbcUrl(database), "root", "test-root")
                .locations("classpath:db/migration")
                .baselineOnMigrate(baseline)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    private static void createDatabase(String database) throws Exception {
        try (Connection connection = connection(null); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database);
            statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
    }

    private static Connection connection(String database) throws Exception {
        return DriverManager.getConnection(jdbcUrl(database), "root", "test-root");
    }

    private static String jdbcUrl(String database) {
        String suffix = database == null ? "" : "/" + database;
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + suffix
                + "?allowMultiQueries=true&serverTimezone=Asia/Shanghai";
    }

    private static int queryInt(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        return queryInt(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = '" + table + "'") == 1;
    }

    private static boolean columnExists(Connection connection, String table, String column) throws Exception {
        return queryInt(connection, "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND column_name = '" + column + "'") == 1;
    }
}

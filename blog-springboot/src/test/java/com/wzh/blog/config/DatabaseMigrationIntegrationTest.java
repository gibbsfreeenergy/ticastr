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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationIntegrationTest {

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306);

    @Test
    void grantsStandaloneStorageMenuAndResourceAccessOnlyToExistingSettingsRoles() throws Exception {
        createDatabase("storage_menu_blog");
        Flyway.configure().dataSource(jdbcUrl("storage_menu_blog"), "root", "test-root")
                .locations("classpath:db/migration").target("21").load().migrate();
        try (Connection connection = connection("storage_menu_blog");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tb_role (id, role_name, role_label, create_time) "
                    + "VALUES (9001, 'Settings', 'storage-test-settings', NOW()), "
                    + "(9002, 'Other', 'storage-test-other', NOW())");
            statement.execute("INSERT INTO tb_menu (id, name, path, component, icon, route_key, create_time, order_num) "
                    + "VALUES (9001, 'Settings', '/setting', '/setting/Setting.vue', 'settings', 'setting', NOW(), 1)");
            statement.execute("INSERT INTO tb_role_menu (role_id, menu_id) VALUES (9001, 9001)");
        }

        migrate("storage_menu_blog", false);
        migrate("storage_menu_blog", false);

        try (Connection connection = connection("storage_menu_blog")) {
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_menu WHERE code = 'storage' "
                    + "AND path = '/storage' AND route_key = 'storage' AND parent_id IS NULL"))
                    .isEqualTo(1);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_role_menu rm "
                    + "JOIN tb_menu m ON m.id = rm.menu_id WHERE m.code = 'storage' AND rm.role_id = 9001"))
                    .isEqualTo(1);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM tb_role_menu rm "
                    + "JOIN tb_menu m ON m.id = rm.menu_id WHERE m.code = 'storage' AND rm.role_id = 9002"))
                    .isZero();
            assertThat(queryInt(connection, "SELECT COUNT(DISTINCT rr.resource_id) FROM tb_role_resource rr "
                    + "JOIN tb_resource resource ON resource.id = rr.resource_id "
                    + "WHERE rr.role_id = 9001 AND resource.parent_id IS NOT NULL "
                    + "AND resource.is_anonymous = 0 AND resource.request_method IS NOT NULL "
                    + "AND (resource.url = '/admin/storage/configs' "
                    + "OR resource.url LIKE '/admin/storage/configs/%')"))
                    .isEqualTo(8);
            assertThat(queryInt(connection, "SELECT COUNT(DISTINCT rr.resource_id) FROM tb_role_resource rr "
                    + "JOIN tb_resource resource ON resource.id = rr.resource_id "
                    + "WHERE rr.role_id = 9002 AND resource.parent_id IS NOT NULL "
                    + "AND resource.is_anonymous = 0 AND resource.request_method IS NOT NULL "
                    + "AND (resource.url = '/admin/storage/configs' "
                    + "OR resource.url LIKE '/admin/storage/configs/%')"))
                    .isZero();
        }
    }

    @Test
    void grantsOutboxResourceAccessToAdministratorsOnly() throws Exception {
        createDatabase("outbox_access_blog");
        Flyway.configure().dataSource(jdbcUrl("outbox_access_blog"), "root", "test-root")
                .locations("classpath:db/migration").target("23").load().migrate();

        migrate("outbox_access_blog", false);
        migrate("outbox_access_blog", false);

        try (Connection connection = connection("outbox_access_blog")) {
            assertThat(queryInt(connection, "SELECT COUNT(DISTINCT rr.resource_id) "
                    + "FROM tb_role_resource rr "
                    + "JOIN tb_role role ON role.id = rr.role_id "
                    + "JOIN tb_resource resource ON resource.id = rr.resource_id "
                    + "WHERE role.role_label = 'admin' "
                    + "AND resource.url IN ('/admin/outbox', '/admin/outbox/metrics', '/admin/outbox/*/retry')"))
                    .isEqualTo(3);
            assertThat(queryInt(connection, "SELECT COUNT(DISTINCT rr.resource_id) "
                    + "FROM tb_role_resource rr "
                    + "JOIN tb_role role ON role.id = rr.role_id "
                    + "JOIN tb_resource resource ON resource.id = rr.resource_id "
                    + "WHERE role.role_label = 'test' "
                    + "AND resource.url IN ('/admin/outbox', '/admin/outbox/metrics', '/admin/outbox/*/retry')"))
                    .isZero();
        }
    }

    @Test
    void grantsArticleContentAndLegacyStorageAccessWithLeastPrivilege() throws Exception {
        createDatabase("content_access_blog");
        Flyway.configure().dataSource(jdbcUrl("content_access_blog"), "root", "test-root")
                .locations("classpath:db/migration").target("24").load().migrate();
        try (Connection connection = connection("content_access_blog");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tb_role (id, role_name, role_label, create_time) "
                    + "VALUES (9001, 'Read only', 'test', NOW())");
        }

        migrate("content_access_blog", false);
        migrate("content_access_blog", false);

        try (Connection connection = connection("content_access_blog")) {
            assertThat(queryInt(connection, "SELECT COUNT(DISTINCT rr.resource_id) "
                    + "FROM tb_role_resource rr "
                    + "JOIN tb_role role ON role.id = rr.role_id "
                    + "JOIN tb_resource resource ON resource.id = rr.resource_id "
                    + "WHERE role.role_label = 'admin' "
                    + "AND resource.url IN ('/admin/articles/*/content', '/admin/articles/*/versions', "
                    + "'/admin/articles/*/versions/*/restore', '/admin/storage/provider', "
                    + "'/admin/storage/providers', '/admin/storage/providers/*/validate')"))
                    .isEqualTo(8);
            assertThat(queryInt(connection, "SELECT COUNT(DISTINCT rr.resource_id) "
                    + "FROM tb_role_resource rr "
                    + "JOIN tb_role role ON role.id = rr.role_id "
                    + "JOIN tb_resource resource ON resource.id = rr.resource_id "
                    + "WHERE role.role_label = 'test' "
                    + "AND resource.request_method = 'GET' "
                    + "AND resource.url IN ('/admin/articles/*/content', '/admin/articles/*/versions')"))
                    .isEqualTo(2);
            assertThat(queryInt(connection, "SELECT COUNT(DISTINCT rr.resource_id) "
                    + "FROM tb_role_resource rr "
                    + "JOIN tb_role role ON role.id = rr.role_id "
                    + "JOIN tb_resource resource ON resource.id = rr.resource_id "
                    + "WHERE role.role_label = 'test' "
                    + "AND resource.url IN ('/admin/articles/*/content', '/admin/articles/*/versions', "
                    + "'/admin/articles/*/versions/*/restore', '/admin/storage/provider', "
                    + "'/admin/storage/providers', '/admin/storage/providers/*/validate') "
                    + "AND NOT (resource.request_method = 'GET' "
                    + "AND resource.url IN ('/admin/articles/*/content', '/admin/articles/*/versions'))"))
                    .isZero();
        }
    }

    @Test
    void migratesAnEmptyDatabase() throws Exception {
        createDatabase("fresh_blog");

        migrate("fresh_blog", false);

        try (Connection connection = connection("fresh_blog")) {
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"))
                    .isGreaterThanOrEqualTo(25);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE()"))
                    .isGreaterThanOrEqualTo(8);
            assertThat(queryInt(connection,
                    "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND index_name = 'idx_article_public_order'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void installsTheContentAssetEngagementAndProviderContracts() throws Exception {
        createDatabase("phase_one_blog");

        migrate("phase_one_blog", false);

        try (Connection connection = connection("phase_one_blog")) {
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tb_article' "
                            + "AND column_name = 'article_content'"))
                    .isZero();
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tb_article' "
                            + "AND column_name = 'content_asset_id'"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name IN "
                            + "('tb_content_asset', 'tb_article_engagement', 'tb_article_like', "
                            + "'tb_storage_provider_config')"))
                    .isEqualTo(4);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tb_storage_provider_config' "
                            + "AND column_name IN ('config_name', 'provider', 'is_active', 'usage_bytes')"))
                    .isEqualTo(4);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tb_storage_bootstrap_state'"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM tb_storage_provider_config WHERE is_active = 1"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM tb_storage_provider_config "
                            + "WHERE id = 1 AND config_name = '本地默认配置' "
                            + "AND provider = 'local' AND is_active = 1"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = 'tb_storage_provider_config' "
                            + "AND constraint_name = 'ck_storage_provider_config_provider' "
                            + "AND constraint_type = 'CHECK'"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name IN ('tb_content_asset', 'tb_media_asset') "
                            + "AND column_name = 'storage_config_id'"))
                    .isEqualTo(2);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM tb_resource WHERE url LIKE '/admin/storage/configs%'"))
                    .isGreaterThanOrEqualTo(5);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tb_article_like' "
                            + "AND index_name = 'PRIMARY' AND seq_in_index = 2"))
                    .isEqualTo(1);
        }
    }

    @Test
    void upgradesTheLegacySeedDatabase() throws Exception {
        createDatabase("legacy_blog");
        try (Connection connection = connection("legacy_blog")) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/legacy-schema.sql"));
        }

        migrate("legacy_blog", true);

        try (Connection connection = connection("legacy_blog")) {
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM tb_article WHERE id = 54")).isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE table_schema = DATABASE() AND constraint_name = 'uk_article_tag'"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tb_storage_provider_config' "
                            + "AND column_name IN ('config_name', 'provider', 'is_active', 'usage_bytes')"))
                    .isEqualTo(4);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM tb_storage_bootstrap_state "
                            + "WHERE id = 1 AND legacy_import_completed = 0 "
                            + "AND legacy_active_provider = 'local' AND completed_at IS NULL"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM tb_storage_provider_config "
                            + "WHERE id = 1 AND config_name = '本地默认配置' "
                            + "AND provider = 'local' AND is_active = 1"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = 'tb_storage_provider_config' "
                            + "AND constraint_name = 'ck_storage_provider_config_provider' "
                            + "AND constraint_type = 'CHECK'"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name IN ('tb_content_asset', 'tb_media_asset') "
                            + "AND column_name = 'storage_config_id'"))
                    .isEqualTo(2);
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
}

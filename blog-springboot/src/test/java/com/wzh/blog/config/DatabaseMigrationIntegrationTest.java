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
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tb_article_like' "
                            + "AND index_name = 'PRIMARY' AND seq_in_index = 2"))
                    .isEqualTo(1);
            assertThat(queryInt(connection,
                    "SELECT COUNT(*) FROM tb_storage_provider_config WHERE id = 1 AND active_provider = 'local'"))
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

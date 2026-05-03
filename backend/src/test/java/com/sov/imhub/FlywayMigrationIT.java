package com.sov.imhub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Flyway V14-V16 迁移脚本在 Testcontainers 上正确执行。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = ImBotHubApplication.class,
        properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class FlywayMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.36").withDatabaseName("tg_query_meta").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsApplied() {
        // 验证 Flyway 迁移历史表存在
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(migrations).isNotEmpty();

        // 验证 V14 迁移（channel_allowlist）
        boolean hasV14 = migrations.stream()
                .anyMatch(m -> "14".equals(m.get("version")));
        assertThat(hasV14).isTrue();

        // 验证 V15 迁移（command_log）
        boolean hasV15 = migrations.stream()
                .anyMatch(m -> "15".equals(m.get("version")));
        assertThat(hasV15).isTrue();

        // 验证 V16 迁移（Bot-Channel 分离）
        boolean hasV16 = migrations.stream()
                .anyMatch(m -> "16".equals(m.get("version")));
        assertThat(hasV16).isTrue();
    }

    @Test
    void t_channel_allowlistTableExists() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SHOW COLUMNS FROM t_channel_allowlist");
        assertThat(columns).isNotEmpty();

        // 验证关键字段存在
        List<String> columnNames = columns.stream()
                .map(c -> (String) c.get("Field"))
                .toList();
        assertThat(columnNames).contains("id", "channel_id", "platform", "external_user_id", "enabled");
    }

    @Test
    void t_command_logTableExists() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SHOW COLUMNS FROM t_command_log");
        assertThat(columns).isNotEmpty();

        // 验证关键字段存在
        List<String> columnNames = columns.stream()
                .map(c -> (String) c.get("Field"))
                .toList();
        assertThat(columnNames).contains("id", "bot_id", "channel_id", "platform", "command", "success", "error_kind");
    }

    @Test
    void t_bot_channelTableExists() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SHOW COLUMNS FROM t_bot_channel");
        assertThat(columns).isNotEmpty();

        // 验证关键字段存在
        List<String> columnNames = columns.stream()
                .map(c -> (String) c.get("Field"))
                .toList();
        assertThat(columnNames).contains("id", "bot_id", "platform", "enabled", "credentials_json");
    }
}

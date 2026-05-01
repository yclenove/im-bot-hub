package com.sov.imhub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pulls MySQL 8 via Testcontainers, applies Flyway, and verifies the Spring context starts.
 * Requires Docker (e.g. CI runners or local Docker Desktop).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = ImBotHubApplication.class,
        properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class ImBotHubApplicationIT {

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
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}

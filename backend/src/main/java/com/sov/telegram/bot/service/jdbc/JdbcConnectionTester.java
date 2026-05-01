package com.sov.telegram.bot.service.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 临时建池验证账号与网络，不写入业务连接池。
 */
@Slf4j
@Service
public class JdbcConnectionTester {

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int IS_VALID_SECONDS = 5;

    public void validate(String jdbcUrl, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(1);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(CONNECT_TIMEOUT_MS);
        ds.setValidationTimeout(5_000);
        ds.setPoolName("tgq-conn-test-" + Thread.currentThread().getId());
        ds.setAutoCommit(true);
        ds.setReadOnly(true);
        try {
            try (Connection c = ds.getConnection()) {
                if (!c.isValid(IS_VALID_SECONDS)) {
                    throw new IllegalArgumentException("连接已建立但校验未通过");
                }
            }
        } catch (SQLException e) {
            log.debug("JDBC test failed: {}", e.toString());
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalArgumentException("连接失败: " + msg);
        } finally {
            ds.close();
        }
    }
}

package com.szzh.loggerserver.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

/**
 * TDengine 数据源配置测试。
 */
class TdengineConfigTest {

    /**
     * 验证 RESTful 驱动配置可以创建数据源。
     */
    @Test
    void shouldCreateRestfulDataSource() {
        LoggerServerProperties properties = buildTdengineProperties(
                "jdbc:TAOS-RS://127.0.0.1:6041/logger?timezone=UTC-8&charset=utf-8&varcharAsString=true",
                "com.taosdata.jdbc.rs.RestfulDriver");
        TdengineConfig config = new TdengineConfig();

        DataSource dataSource = config.tdengineDataSource(properties);

        Assertions.assertTrue(dataSource instanceof HikariDataSource);
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        try {
            Assertions.assertEquals(properties.getTdengine().getJdbcUrl(), hikariDataSource.getJdbcUrl());
            Assertions.assertEquals(properties.getTdengine().getDriverClassName(), hikariDataSource.getDriverClassName());
        } finally {
            hikariDataSource.close();
        }
    }

    /**
     * 验证 TDengine URL 前缀和驱动类型不匹配时快速失败。
     */
    @Test
    void shouldRejectMismatchedJdbcUrlAndDriver() {
        LoggerServerProperties properties = buildTdengineProperties(
                "jdbc:TAOS-RS://127.0.0.1:6041/logger",
                "com.taosdata.jdbc.TSDBDriver");
        TdengineConfig config = new TdengineConfig();

        Assertions.assertThrows(IllegalStateException.class, () -> config.tdengineDataSource(properties));
    }

    /**
     * 验证低版本兼容模式下拒绝 WebSocket JDBC URL。
     */
    @Test
    void shouldRejectWebSocketJdbcUrl() {
        LoggerServerProperties properties = buildTdengineProperties(
                "jdbc:TAOS-WS://127.0.0.1:6041/logger",
                "com.taosdata.jdbc.ws.WebSocketDriver");
        TdengineConfig config = new TdengineConfig();

        Assertions.assertThrows(IllegalStateException.class, () -> config.tdengineDataSource(properties));
    }

    /**
     * 构造 TDengine 测试配置。
     *
     * @param jdbcUrl JDBC 地址。
     * @param driverClassName 驱动类名。
     * @return logger-server 配置。
     */
    private LoggerServerProperties buildTdengineProperties(String jdbcUrl, String driverClassName) {
        LoggerServerProperties properties = new LoggerServerProperties();
        properties.getTdengine().setJdbcUrl(jdbcUrl);
        properties.getTdengine().setUsername("root");
        properties.getTdengine().setPassword("taosdata");
        properties.getTdengine().setDriverClassName(driverClassName);
        return properties;
    }
}

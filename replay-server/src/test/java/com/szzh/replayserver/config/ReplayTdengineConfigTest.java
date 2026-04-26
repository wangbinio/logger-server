package com.szzh.replayserver.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 回放侧 TDengine 配置测试。
 */
class ReplayTdengineConfigTest {

    /**
     * 验证回放服务会使用独立配置创建 TDengine 数据源。
     */
    @Test
    void shouldCreateTdengineDataSourceFromReplayProperties() {
        ReplayServerProperties properties = new ReplayServerProperties();
        properties.getTdengine().setJdbcUrl("jdbc:TAOS-WS://127.0.0.1:6041/logger");
        properties.getTdengine().setUsername("root");
        properties.getTdengine().setPassword("taosdata");
        properties.getTdengine().setMaximumPoolSize(3);
        properties.getTdengine().setConnectionTimeoutMs(12345L);
        ReplayTdengineConfig config = new ReplayTdengineConfig();

        DataSource dataSource = config.tdengineDataSource(properties);

        Assertions.assertTrue(dataSource instanceof HikariDataSource);
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        Assertions.assertEquals("jdbc:TAOS-WS://127.0.0.1:6041/logger", hikariDataSource.getJdbcUrl());
        Assertions.assertEquals("root", hikariDataSource.getUsername());
        Assertions.assertEquals("taosdata", hikariDataSource.getPassword());
        Assertions.assertEquals(3, hikariDataSource.getMaximumPoolSize());
        Assertions.assertEquals(12345L, hikariDataSource.getConnectionTimeout());
        hikariDataSource.close();
    }

    /**
     * 验证配置不完整时拒绝创建数据源。
     */
    @Test
    void shouldRejectMissingTdengineJdbcUrl() {
        ReplayTdengineConfig config = new ReplayTdengineConfig();
        ReplayServerProperties properties = new ReplayServerProperties();
        properties.getTdengine().setUsername("root");

        Assertions.assertThrows(IllegalStateException.class, () -> config.tdengineDataSource(properties));
    }

    /**
     * 验证回放侧 JDBC 模板使用同一个 TDengine 数据源。
     */
    @Test
    void shouldCreateJdbcTemplateFromReplayDataSource() {
        ReplayTdengineConfig config = new ReplayTdengineConfig();
        DataSource dataSource = org.mockito.Mockito.mock(DataSource.class);

        JdbcTemplate jdbcTemplate = config.jdbcTemplate(dataSource);

        Assertions.assertSame(dataSource, jdbcTemplate.getDataSource());
    }
}

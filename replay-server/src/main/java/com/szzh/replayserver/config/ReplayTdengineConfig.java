package com.szzh.replayserver.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 回放侧 TDengine 基础配置。
 */
@Configuration
public class ReplayTdengineConfig {

    /**
     * 创建回放侧 TDengine 数据源。
     *
     * @param properties 回放服务配置。
     * @return TDengine 数据源。
     */
    @Bean
    public DataSource tdengineDataSource(ReplayServerProperties properties) {
        ReplayServerProperties.Tdengine tdengine = properties.getTdengine();
        validateTdengineConfig(tdengine);

        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(tdengine.getDriverClassName())
                .url(tdengine.getJdbcUrl())
                .username(tdengine.getUsername())
                .password(tdengine.getPassword())
                .build();
        dataSource.setInitializationFailTimeout(-1L);
        dataSource.setMinimumIdle(0);
        dataSource.setMaximumPoolSize(tdengine.getMaximumPoolSize());
        dataSource.setConnectionTimeout(tdengine.getConnectionTimeoutMs());
        return dataSource;
    }

    /**
     * 创建回放侧 JDBC 模板。
     *
     * @param dataSource TDengine 数据源。
     * @return JDBC 模板。
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 校验 TDengine 基础配置是否完整。
     *
     * @param tdengine TDengine 配置。
     */
    private void validateTdengineConfig(ReplayServerProperties.Tdengine tdengine) {
        if (!StringUtils.hasText(tdengine.getJdbcUrl())) {
            throw new IllegalStateException("replay-server.tdengine.jdbc-url 未配置");
        }
        if (!StringUtils.hasText(tdengine.getUsername())) {
            throw new IllegalStateException("replay-server.tdengine.username 未配置");
        }
        if (!StringUtils.hasText(tdengine.getDriverClassName())) {
            throw new IllegalStateException("replay-server.tdengine.driver-class-name 未配置");
        }
    }
}

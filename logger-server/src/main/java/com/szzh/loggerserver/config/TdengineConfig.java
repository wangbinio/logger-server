package com.szzh.loggerserver.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Locale;

/**
 * TDengine 基础配置。
 */
@Configuration
@EnableConfigurationProperties(LoggerServerProperties.class)
public class TdengineConfig {

    private static final String JDBC_TAOS_PREFIX = "JDBC:TAOS://";

    private static final String JDBC_TSDB_PREFIX = "JDBC:TSDB://";

    private static final String JDBC_TAOS_RS_PREFIX = "JDBC:TAOS-RS://";

    private static final String JDBC_TAOS_WS_PREFIX = "JDBC:TAOS-WS://";

    private static final String TSDB_DRIVER_CLASS_NAME = "com.taosdata.jdbc.TSDBDriver";

    private static final String RESTFUL_DRIVER_CLASS_NAME = "com.taosdata.jdbc.rs.RestfulDriver";

    /**
     * 创建 TDengine 数据源。
     *
     * @param properties logger-server 配置。
     * @return TDengine 数据源。
     */
    @Bean
    public DataSource tdengineDataSource(LoggerServerProperties properties) {
        LoggerServerProperties.Tdengine tdengine = properties.getTdengine();
        validateTdengineConfig(tdengine);

        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(tdengine.getDriverClassName())
                .url(tdengine.getJdbcUrl())
                .username(tdengine.getUsername())
                .password(tdengine.getPassword())
                .build();
        // Phase 00 只需要完成骨架初始化，不要求本地必须已启动 TDengine。
        dataSource.setInitializationFailTimeout(-1L);
        dataSource.setMinimumIdle(0);
        dataSource.setMaximumPoolSize(tdengine.getMaximumPoolSize());
        dataSource.setConnectionTimeout(tdengine.getConnectionTimeoutMs());
        return dataSource;
    }

    /**
     * 创建 JDBC 模板。
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
    private void validateTdengineConfig(LoggerServerProperties.Tdengine tdengine) {
        if (!StringUtils.hasText(tdengine.getJdbcUrl())) {
            throw new IllegalStateException("logger-server.tdengine.jdbc-url 未配置");
        }
        if (!StringUtils.hasText(tdengine.getUsername())) {
            throw new IllegalStateException("logger-server.tdengine.username 未配置");
        }
        if (!StringUtils.hasText(tdengine.getDriverClassName())) {
            throw new IllegalStateException("logger-server.tdengine.driver-class-name 未配置");
        }
        validateDriverMatchesJdbcUrl(tdengine);
    }

    /**
     * 校验 JDBC URL 协议与驱动类是否匹配。
     *
     * @param tdengine TDengine 配置。
     */
    private void validateDriverMatchesJdbcUrl(LoggerServerProperties.Tdengine tdengine) {
        String jdbcUrl = tdengine.getJdbcUrl().trim().toUpperCase(Locale.ENGLISH);
        String driverClassName = tdengine.getDriverClassName().trim();
        if (jdbcUrl.startsWith(JDBC_TAOS_RS_PREFIX)) {
            requireDriverClassName(driverClassName, RESTFUL_DRIVER_CLASS_NAME, "TAOS-RS");
            return;
        }
        if (jdbcUrl.startsWith(JDBC_TAOS_WS_PREFIX)) {
            throw new IllegalStateException("当前 TDengine JDBC 版本不支持 TAOS-WS，请使用 TAOS-RS");
        }
        if (jdbcUrl.startsWith(JDBC_TAOS_PREFIX) || jdbcUrl.startsWith(JDBC_TSDB_PREFIX)) {
            requireDriverClassName(driverClassName, TSDB_DRIVER_CLASS_NAME, "TAOS");
            return;
        }
        throw new IllegalStateException("不支持的 TDengine JDBC URL: " + tdengine.getJdbcUrl());
    }

    /**
     * 校验驱动类名是否为期望值。
     *
     * @param actualDriverClassName 实际驱动类名。
     * @param expectedDriverClassName 期望驱动类名。
     * @param protocolName 协议名称。
     */
    private void requireDriverClassName(String actualDriverClassName,
                                        String expectedDriverClassName,
                                        String protocolName) {
        if (!expectedDriverClassName.equals(actualDriverClassName)) {
            throw new IllegalStateException("TDengine " + protocolName
                    + " 连接必须使用驱动 " + expectedDriverClassName
                    + "，当前配置为 " + actualDriverClassName);
        }
    }
}

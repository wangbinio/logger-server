package com.szzh.loggerserver.service;

import com.szzh.loggerserver.support.constant.TdengineConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * TDengine 建表服务。
 */
@Service
public class TdengineSchemaService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 TDengine 建表服务。
     *
     * @param jdbcTemplate JDBC 模板。
     */
    public TdengineSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
    }

    /**
     * 按实例创建超级表。
     *
     * @param instanceId 仿真实例 ID。
     */
    public void createStableIfAbsent(String instanceId) {
        String sql = TdengineConstants.buildCreateStableSql(instanceId);
        jdbcTemplate.execute(sql);
    }
}

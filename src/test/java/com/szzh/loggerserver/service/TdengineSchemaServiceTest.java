package com.szzh.loggerserver.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TDengine 建表服务测试。
 */
class TdengineSchemaServiceTest {

    /**
     * 验证会按实例创建超级表。
     */
    @Test
    void shouldCreateStableForInstance() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        TdengineSchemaService schemaService = new TdengineSchemaService(jdbcTemplate);

        schemaService.createStableIfAbsent("instance-001");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).execute(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        Assertions.assertTrue(sql.contains("CREATE STABLE IF NOT EXISTS situation_instance_001"));
        Assertions.assertTrue(sql.contains("sender_id INT"));
        Assertions.assertTrue(sql.contains("msgtype INT"));
        Assertions.assertTrue(sql.contains("msgcode INT"));
    }

    /**
     * 验证非法实例 ID 会被拒绝。
     */
    @Test
    void shouldRejectIllegalInstanceId() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        TdengineSchemaService schemaService = new TdengineSchemaService(jdbcTemplate);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> schemaService.createStableIfAbsent(" "));
        Mockito.verifyNoInteractions(jdbcTemplate);
    }
}

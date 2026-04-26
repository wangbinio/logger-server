package com.szzh.loggerserver.support.constant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TDengine 常量测试。
 */
class TdengineConstantsTest {

    /**
     * 验证控制时间点表名复用实例 ID 清洗规则。
     */
    @Test
    void shouldBuildTimeControlTableNameWithSanitizedInstanceId() {
        Assertions.assertEquals("time_control_instance_001",
                TdengineConstants.buildTimeControlTableName("instance-001"));
    }

    /**
     * 验证控制时间点建表 SQL 包含完整字段定义。
     */
    @Test
    void shouldBuildCreateTimeControlTableSql() {
        String sql = TdengineConstants.buildCreateTimeControlTableSql("instance-001");

        Assertions.assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS time_control_instance_001"));
        Assertions.assertTrue(sql.contains("ts TIMESTAMP"));
        Assertions.assertTrue(sql.contains("simtime BIGINT"));
        Assertions.assertTrue(sql.contains("rate DOUBLE"));
        Assertions.assertTrue(sql.contains("sender_id INT"));
        Assertions.assertTrue(sql.contains("msgtype INT"));
        Assertions.assertTrue(sql.contains("msgcode INT"));
    }

    /**
     * 验证控制时间点写入 SQL 使用 NOW 和预期参数数量。
     */
    @Test
    void shouldBuildInsertTimeControlSql() {
        String sql = TdengineConstants.buildInsertTimeControlSql("instance-001");

        Assertions.assertEquals("INSERT INTO time_control_instance_001 VALUES (NOW, ?, ?, ?, ?, ?)", sql);
    }

    /**
     * 验证空白实例 ID 会被拒绝。
     */
    @Test
    void shouldRejectBlankInstanceIdWhenBuildTimeControlSql() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> TdengineConstants.buildTimeControlTableName(" "));
    }
}

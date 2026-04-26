package com.szzh.common.tdengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TDengine 命名规则测试。
 */
class TdengineNamingTest {

    /**
     * 验证实例超级表名称复用清洗规则。
     */
    @Test
    void shouldBuildStableName() {
        Assertions.assertEquals("situation_instance_001", TdengineNaming.buildStableName("instance-001"));
    }

    /**
     * 验证子表名称包含消息维度和实例 ID。
     */
    @Test
    void shouldBuildSubTableName() {
        Assertions.assertEquals("situation_1001_2_7_instance_001",
                TdengineNaming.buildSubTableName("instance-001", 1001, 2, 7));
    }

    /**
     * 验证控制时间点表名称复用清洗规则。
     */
    @Test
    void shouldBuildTimeControlTableName() {
        Assertions.assertEquals("time_control_instance_001",
                TdengineNaming.buildTimeControlTableName("instance-001"));
    }

    /**
     * 验证空白标识会被拒绝。
     */
    @Test
    void shouldRejectBlankIdentifier() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> TdengineNaming.sanitizeIdentifier(" "));
    }
}

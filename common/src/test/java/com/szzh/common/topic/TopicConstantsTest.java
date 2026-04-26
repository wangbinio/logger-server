package com.szzh.common.topic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Topic 常量测试。
 */
class TopicConstantsTest {

    /**
     * 验证可以拼接实例控制 topic。
     */
    @Test
    void shouldBuildInstanceBroadcastTopic() {
        String topic = TopicConstants.buildInstanceBroadcastTopic("instance-001");

        Assertions.assertEquals("broadcast-instance-001", topic);
    }

    /**
     * 验证可以拼接实例态势 topic。
     */
    @Test
    void shouldBuildInstanceSituationTopic() {
        String topic = TopicConstants.buildInstanceSituationTopic("instance-001");

        Assertions.assertEquals("situation-instance-001", topic);
    }

    /**
     * 验证空实例 ID 会抛出异常。
     */
    @Test
    void shouldThrowWhenInstanceIdBlank() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> TopicConstants.buildInstanceBroadcastTopic(" "));
    }
}

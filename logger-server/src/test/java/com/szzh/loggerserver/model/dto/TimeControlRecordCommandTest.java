package com.szzh.loggerserver.model.dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 控制时间点写入命令测试。
 */
class TimeControlRecordCommandTest {

    /**
     * 验证合法命令会保留审计字段和控制语义。
     */
    @Test
    void shouldCreateCommandWithAuditFields() {
        TimeControlRecordCommand command = TimeControlRecordCommand.builder()
                .instanceId(" instance-001 ")
                .simTime(123L)
                .rate(0D)
                .senderId(11)
                .messageType(2100)
                .messageCode(7)
                .build();

        Assertions.assertEquals("instance-001", command.getInstanceId());
        Assertions.assertEquals(123L, command.getSimTime());
        Assertions.assertEquals(0D, command.getRate());
        Assertions.assertEquals(11, command.getSenderId());
        Assertions.assertEquals(2100, command.getMessageType());
        Assertions.assertEquals(7, command.getMessageCode());
    }

    /**
     * 验证空白实例 ID 会被拒绝。
     */
    @Test
    void shouldRejectBlankInstanceId() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> TimeControlRecordCommand.builder()
                        .instanceId(" ")
                        .simTime(1L)
                        .rate(1D)
                        .build());
    }

    /**
     * 验证负数倍率会被拒绝。
     */
    @Test
    void shouldRejectNegativeRate() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> TimeControlRecordCommand.builder()
                        .instanceId("instance-001")
                        .simTime(1L)
                        .rate(-0.1D)
                        .build());
    }
}

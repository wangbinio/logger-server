package com.szzh.replayserver.model.query;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 回放查询模型测试。
 */
class ReplayQueryModelTest {

    /**
     * 验证子表描述对象保持不可变字段并支持类型重建。
     */
    @Test
    void shouldCreateReplayTableDescriptor() {
        ReplayTableDescriptor descriptor =
                new ReplayTableDescriptor("situation_1001_2_7_instance_001", 7, 1001, 2, ReplayTableType.EVENT);

        Assertions.assertEquals("situation_1001_2_7_instance_001", descriptor.getTableName());
        Assertions.assertEquals(7, descriptor.getSenderId());
        Assertions.assertEquals(1001, descriptor.getMessageType());
        Assertions.assertEquals(2, descriptor.getMessageCode());
        Assertions.assertEquals(ReplayTableType.EVENT, descriptor.getTableType());
        Assertions.assertEquals(ReplayTableType.PERIODIC,
                descriptor.withType(ReplayTableType.PERIODIC).getTableType());
    }

    /**
     * 验证非法子表描述会被拒绝。
     */
    @Test
    void shouldRejectInvalidReplayTableDescriptor() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ReplayTableDescriptor(" ", 7, 1001, 2, ReplayTableType.EVENT));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ReplayTableDescriptor("table", 7, 1001, 2, null));
    }

    /**
     * 验证回放帧复制原始载荷，避免外部修改破坏不可变语义。
     */
    @Test
    void shouldCopyReplayFrameRawData() {
        byte[] rawData = new byte[]{1, 2, 3};
        ReplayFrame frame = new ReplayFrame("table", 7, 1001, 2, 123L, rawData);
        rawData[0] = 9;
        byte[] fromGetter = frame.getRawData();
        fromGetter[1] = 8;

        Assertions.assertArrayEquals(new byte[]{1, 2, 3}, frame.getRawData());
    }

    /**
     * 验证分页游标按已消费数量推进。
     */
    @Test
    void shouldAdvanceReplayCursor() {
        ReplayCursor cursor = new ReplayCursor("table", 100, 20);

        ReplayCursor advanced = cursor.advance(7);

        Assertions.assertEquals("table", advanced.getTableName());
        Assertions.assertEquals(100, advanced.getLimit());
        Assertions.assertEquals(27, advanced.getOffset());
    }

    /**
     * 验证时间范围会计算持续时间并拒绝负区间。
     */
    @Test
    void shouldCreateReplayTimeRange() {
        ReplayTimeRange range = new ReplayTimeRange(100L, 350L);

        Assertions.assertEquals(100L, range.getStartTime());
        Assertions.assertEquals(350L, range.getEndTime());
        Assertions.assertEquals(250L, range.getDuration());
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ReplayTimeRange(400L, 300L));
    }
}

package com.szzh.replayserver.service;

import com.szzh.replayserver.model.query.ReplayFrame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 回放帧归并服务测试。
 */
class ReplayFrameMergeServiceTest {

    /**
     * 验证多表帧按仿真时间和稳定排序字段归并。
     */
    @Test
    void shouldMergeFramesBySimTimeAndStableFields() {
        ReplayFrameMergeService mergeService = new ReplayFrameMergeService();
        ReplayFrame later = frame("table_b", 9, 1002, 1, 300L);
        ReplayFrame first = frame("table_c", 7, 1001, 1, 100L);
        ReplayFrame sameTimeByMessageType = frame("table_d", 8, 1000, 9, 200L);
        ReplayFrame sameTimeByMessageCode = frame("table_a", 6, 1001, 1, 200L);
        ReplayFrame sameTimeBySender = frame("table_e", 5, 1001, 2, 200L);

        List<ReplayFrame> merged = mergeService.merge(Arrays.asList(
                Arrays.asList(later, sameTimeBySender),
                Arrays.asList(first, sameTimeByMessageCode),
                Collections.singletonList(sameTimeByMessageType)));

        Assertions.assertEquals(first, merged.get(0));
        Assertions.assertEquals(sameTimeByMessageType, merged.get(1));
        Assertions.assertEquals(sameTimeByMessageCode, merged.get(2));
        Assertions.assertEquals(sameTimeBySender, merged.get(3));
        Assertions.assertEquals(later, merged.get(4));
    }

    /**
     * 验证空输入返回空结果。
     */
    @Test
    void shouldReturnEmptyListWhenInputIsEmpty() {
        ReplayFrameMergeService mergeService = new ReplayFrameMergeService();

        Assertions.assertTrue(mergeService.merge(null).isEmpty());
        Assertions.assertTrue(mergeService.merge(Collections.emptyList()).isEmpty());
    }

    /**
     * 构造测试帧。
     *
     * @param tableName 表名。
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param simTime 仿真时间。
     * @return 回放帧。
     */
    private ReplayFrame frame(String tableName, int senderId, int messageType, int messageCode, long simTime) {
        return new ReplayFrame(tableName, senderId, messageType, messageCode, simTime, new byte[]{1});
    }
}

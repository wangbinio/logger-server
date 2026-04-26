package com.szzh.replayserver.repository;

import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 回放帧查询 Repository 测试。
 */
class ReplayFrameRepositoryTest {

    private final ReplayTableDescriptor descriptor =
            new ReplayTableDescriptor("situation_1001_2_7_instance_001", 7, 1001, 2, ReplayTableType.EVENT);

    /**
     * 验证连续回放窗口使用左开右闭边界并分页。
     */
    @Test
    void shouldQueryWindowFramesWithOpenClosedRange() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(),
                        Mockito.eq(100L), Mockito.eq(200L), Mockito.eq(10), Mockito.eq(20)))
                .thenReturn(Collections.singletonList(row(150L, new byte[]{1})));

        List<ReplayFrame> frames = repository.findWindowFrames(
                descriptor,
                100L,
                200L,
                new ReplayCursor(descriptor.getTableName(), 10, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture(),
                Mockito.eq(100L), Mockito.eq(200L), Mockito.eq(10), Mockito.eq(20));
        Assertions.assertTrue(sqlCaptor.getValue().contains("WHERE simtime > ? AND simtime <= ?"));
        Assertions.assertEquals(150L, frames.get(0).getSimTime());
        Assertions.assertArrayEquals(new byte[]{1}, frames.get(0).getRawData());
    }

    /**
     * 验证向后跳转事件查询使用闭区间。
     */
    @Test
    void shouldQueryBackwardJumpEventFramesWithClosedRange() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(),
                        Mockito.eq(100L), Mockito.eq(200L), Mockito.eq(5), Mockito.eq(0)))
                .thenReturn(Collections.emptyList());

        repository.findBackwardJumpEventFrames(descriptor, 100L, 200L, new ReplayCursor(descriptor.getTableName(), 5, 0));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture(),
                Mockito.eq(100L), Mockito.eq(200L), Mockito.eq(5), Mockito.eq(0));
        Assertions.assertTrue(sqlCaptor.getValue().contains("WHERE simtime >= ? AND simtime <= ?"));
    }

    /**
     * 验证向前跳转事件查询使用左开右闭区间。
     */
    @Test
    void shouldQueryForwardJumpEventFramesWithOpenClosedRange() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(),
                        Mockito.eq(150L), Mockito.eq(300L), Mockito.eq(5), Mockito.eq(10)))
                .thenReturn(Collections.emptyList());

        repository.findForwardJumpEventFrames(descriptor, 150L, 300L, new ReplayCursor(descriptor.getTableName(), 5, 10));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture(),
                Mockito.eq(150L), Mockito.eq(300L), Mockito.eq(5), Mockito.eq(10));
        Assertions.assertTrue(sqlCaptor.getValue().contains("WHERE simtime > ? AND simtime <= ?"));
    }

    /**
     * 验证周期表最后一帧查询使用右闭边界和倒序 LIMIT 1。
     */
    @Test
    void shouldQueryPeriodicLastFrameBeforeTarget() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.eq(300L)))
                .thenReturn(Collections.singletonList(row(280L, new byte[]{9})));

        Optional<ReplayFrame> frame = repository.findPeriodicLastFrame(descriptor, 300L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture(), Mockito.eq(300L));
        Assertions.assertTrue(sqlCaptor.getValue().contains("WHERE simtime <= ?"));
        Assertions.assertTrue(sqlCaptor.getValue().contains("ORDER BY simtime DESC"));
        Assertions.assertTrue(sqlCaptor.getValue().contains("LIMIT 1"));
        Assertions.assertTrue(frame.isPresent());
        Assertions.assertEquals(280L, frame.get().getSimTime());
    }

    /**
     * 创建帧查询结果行。
     *
     * @param simTime 仿真时间。
     * @param rawData 原始载荷。
     * @return 查询结果行。
     */
    private Map<String, Object> row(long simTime, byte[] rawData) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("simtime", simTime);
        row.put("rawdata", rawData);
        return row;
    }
}

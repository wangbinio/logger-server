package com.szzh.replayserver.repository;

import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
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
    void shouldQueryWindowFramesWithOpenClosedRange() throws SQLException {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        ResultSet resultSet = mockFrameResultSet(150L, new byte[]{1});
        mockQueryRows(jdbcTemplate, Collections.singletonList(resultSet));

        List<ReplayFrame> frames = repository.findWindowFrames(
                descriptor,
                100L,
                200L,
                new ReplayCursor(descriptor.getTableName(), 10, 20));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(),
                Mockito.<RowMapper<ReplayFrame>>any(),
                Mockito.eq(100L), Mockito.eq(200L), Mockito.eq(10), Mockito.eq(20));
        Assertions.assertTrue(sqlCaptor.getValue().contains("WHERE simtime > ? AND simtime <= ?"));
        Assertions.assertEquals(150L, frames.get(0).getSimTime());
        Assertions.assertArrayEquals(new byte[]{1}, frames.get(0).getRawData());
        Mockito.verify(resultSet).getBytes("rawdata");
    }

    /**
     * 验证向后跳转事件查询使用闭区间。
     */
    @Test
    void shouldQueryBackwardJumpEventFramesWithClosedRange() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                        Mockito.<RowMapper<ReplayFrame>>any(),
                        Mockito.eq(100L), Mockito.eq(200L), Mockito.eq(5), Mockito.eq(0)))
                .thenReturn(Collections.emptyList());

        repository.findBackwardJumpEventFrames(descriptor, 100L, 200L, new ReplayCursor(descriptor.getTableName(), 5, 0));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(),
                Mockito.<RowMapper<ReplayFrame>>any(),
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
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                        Mockito.<RowMapper<ReplayFrame>>any(),
                        Mockito.eq(150L), Mockito.eq(300L), Mockito.eq(5), Mockito.eq(10)))
                .thenReturn(Collections.emptyList());

        repository.findForwardJumpEventFrames(descriptor, 150L, 300L, new ReplayCursor(descriptor.getTableName(), 5, 10));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(),
                Mockito.<RowMapper<ReplayFrame>>any(),
                Mockito.eq(150L), Mockito.eq(300L), Mockito.eq(5), Mockito.eq(10));
        Assertions.assertTrue(sqlCaptor.getValue().contains("WHERE simtime > ? AND simtime <= ?"));
    }

    /**
     * 验证周期表最后一帧查询使用右闭边界和倒序 LIMIT 1。
     */
    @Test
    void shouldQueryPeriodicLastFrameBeforeTarget() throws SQLException {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        mockQueryRows(jdbcTemplate, Collections.singletonList(mockFrameResultSet(280L, new byte[]{9})));

        Optional<ReplayFrame> frame = repository.findPeriodicLastFrame(descriptor, 300L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), Mockito.<RowMapper<ReplayFrame>>any(), Mockito.eq(300L));
        Assertions.assertTrue(sqlCaptor.getValue().contains("WHERE simtime <= ?"));
        Assertions.assertTrue(sqlCaptor.getValue().contains("ORDER BY simtime DESC"));
        Assertions.assertTrue(sqlCaptor.getValue().contains("LIMIT 1"));
        Assertions.assertTrue(frame.isPresent());
        Assertions.assertEquals(280L, frame.get().getSimTime());
    }

    /**
     * 验证低版本 RESTful 驱动未通过 getBytes 返回 rawdata 时可以回退到 getObject。
     *
     * @throws SQLException SQL 异常。
     */
    @Test
    void shouldReadRawdataFromObjectWhenDriverGetBytesReturnsNull() throws SQLException {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayFrameRepository repository = new ReplayFrameRepository(jdbcTemplate);
        ResultSet resultSet = mockFrameResultSet(360L, null);
        Mockito.when(resultSet.getObject("rawdata")).thenReturn("{\"simTime\":360}");
        mockQueryRows(jdbcTemplate, Collections.singletonList(resultSet));

        List<ReplayFrame> frames = repository.findWindowFrames(
                descriptor,
                300L,
                400L,
                new ReplayCursor(descriptor.getTableName(), 10, 0));

        Assertions.assertEquals(360L, frames.get(0).getSimTime());
        Assertions.assertArrayEquals("{\"simTime\":360}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                frames.get(0).getRawData());
        Mockito.verify(resultSet).getObject("rawdata");
    }

    /**
     * 模拟 JDBC 查询并通过 RowMapper 读取 ResultSet。
     *
     * @param jdbcTemplate JDBC 模板。
     * @param rows 模拟结果集行。
     */
    private void mockQueryRows(JdbcTemplate jdbcTemplate, List<ResultSet> rows) {
        Mockito.when(jdbcTemplate.query(Mockito.anyString(), Mockito.<RowMapper<ReplayFrame>>any(), Mockito.any()))
                .thenAnswer(new Answer<List<ReplayFrame>>() {
                    /**
                     * 调用被测 RowMapper 生成回放帧列表。
                     *
                     * @param invocation Mockito 调用信息。
                     * @return 回放帧列表。
                     * @throws Throwable 映射异常。
                     */
                    @Override
                    public List<ReplayFrame> answer(InvocationOnMock invocation) throws Throwable {
                        RowMapper<ReplayFrame> rowMapper = invocation.getArgument(1);
                        return mapRows(rowMapper, rows);
                    }
                });
    }

    /**
     * 执行 RowMapper 映射。
     *
     * @param rowMapper 回放帧映射器。
     * @param rows 模拟结果集行。
     * @return 回放帧列表。
     * @throws SQLException SQL 异常。
     */
    private List<ReplayFrame> mapRows(RowMapper<ReplayFrame> rowMapper, List<ResultSet> rows) throws SQLException {
        List<ReplayFrame> frames = new java.util.ArrayList<ReplayFrame>();
        for (int index = 0; index < rows.size(); index++) {
            frames.add(rowMapper.mapRow(rows.get(index), index));
        }
        return frames;
    }

    /**
     * 创建模拟帧结果集。
     *
     * @param simTime 仿真时间。
     * @param rawData 原始载荷。
     * @return 模拟结果集。
     * @throws SQLException SQL 异常。
     */
    private ResultSet mockFrameResultSet(long simTime, byte[] rawData) throws SQLException {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.getLong("simtime")).thenReturn(simTime);
        Mockito.when(resultSet.getBytes("rawdata")).thenReturn(rawData);
        return resultSet;
    }
}

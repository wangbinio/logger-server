package com.szzh.replayserver.repository;

import com.szzh.common.exception.BusinessException;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

/**
 * 回放控制时间点查询测试。
 */
class ReplayTimeControlRepositoryTest {

    /**
     * 验证优先从控制表读取开始时间和停止控制点结束时间。
     */
    @Test
    void shouldResolveTimeRangeFromControlTable() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTimeControlRepository repository = new ReplayTimeControlRepository(jdbcTemplate, new ReplayServerProperties());
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("WHERE rate > 0"), Mockito.eq(Long.class)))
                .thenReturn(100L);
        Mockito.when(jdbcTemplate.queryForObject(
                        Mockito.contains("WHERE msgtype = ? AND msgcode = ?"),
                        Mockito.eq(Long.class),
                        Mockito.eq(0),
                        Mockito.eq(1)))
                .thenReturn(350L);

        ReplayTimeRange range = repository.resolveTimeRange("instance-001");

        Assertions.assertEquals(100L, range.getStartTime());
        Assertions.assertEquals(350L, range.getEndTime());
        Assertions.assertEquals(250L, range.getDuration());
    }

    /**
     * 验证缺少开始控制点时降级查询态势表最小 simtime。
     */
    @Test
    void shouldFallbackToSituationMinWhenStartControlMissing() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTimeControlRepository repository = new ReplayTimeControlRepository(jdbcTemplate, new ReplayServerProperties());
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("WHERE rate > 0"), Mockito.eq(Long.class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("MIN(simtime)"), Mockito.eq(Long.class)))
                .thenReturn(120L);
        Mockito.when(jdbcTemplate.queryForObject(
                        Mockito.contains("WHERE msgtype = ? AND msgcode = ?"),
                        Mockito.eq(Long.class),
                        Mockito.eq(0),
                        Mockito.eq(1)))
                .thenReturn(350L);

        ReplayTimeRange range = repository.resolveTimeRange("instance-001");

        Assertions.assertEquals(120L, range.getStartTime());
        Assertions.assertEquals(350L, range.getEndTime());
    }

    /**
     * 验证缺少停止控制点时降级查询态势表最大 simtime。
     */
    @Test
    void shouldFallbackToSituationMaxWhenStopControlMissing() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTimeControlRepository repository = new ReplayTimeControlRepository(jdbcTemplate, new ReplayServerProperties());
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("WHERE rate > 0"), Mockito.eq(Long.class)))
                .thenReturn(100L);
        Mockito.when(jdbcTemplate.queryForObject(
                        Mockito.contains("WHERE msgtype = ? AND msgcode = ?"),
                        Mockito.eq(Long.class),
                        Mockito.eq(0),
                        Mockito.eq(1)))
                .thenThrow(new EmptyResultDataAccessException(1));
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("MAX(simtime)"), Mockito.eq(Long.class)))
                .thenReturn(420L);

        ReplayTimeRange range = repository.resolveTimeRange("instance-001");

        Assertions.assertEquals(100L, range.getStartTime());
        Assertions.assertEquals(420L, range.getEndTime());
    }

    /**
     * 验证控制时间点表不存在时降级查询态势表起止时间。
     */
    @Test
    void shouldFallbackToSituationRangeWhenTimeControlTableMissing() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTimeControlRepository repository = new ReplayTimeControlRepository(jdbcTemplate, new ReplayServerProperties());
        BadSqlGrammarException missingTableException = new BadSqlGrammarException(
                "query",
                "SELECT simtime FROM time_control_instance_001",
                new SQLException("Table time_control_instance_001 does not exist"));
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("WHERE rate > 0"), Mockito.eq(Long.class)))
                .thenThrow(missingTableException);
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("MIN(simtime)"), Mockito.eq(Long.class)))
                .thenReturn(120L);
        Mockito.when(jdbcTemplate.queryForObject(
                        Mockito.contains("WHERE msgtype = ? AND msgcode = ?"),
                        Mockito.eq(Long.class),
                        Mockito.eq(0),
                        Mockito.eq(1)))
                .thenThrow(missingTableException);
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("MAX(simtime)"), Mockito.eq(Long.class)))
                .thenReturn(420L);

        ReplayTimeRange range = repository.resolveTimeRange("instance-001");

        Assertions.assertEquals(120L, range.getStartTime());
        Assertions.assertEquals(420L, range.getEndTime());
    }

    /**
     * 验证 TDengine 连接失败不会被误判为控制表缺失。
     */
    @Test
    void shouldPropagateConnectionFailureWithoutFallback() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTimeControlRepository repository = new ReplayTimeControlRepository(jdbcTemplate, new ReplayServerProperties());
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("WHERE rate > 0"), Mockito.eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("connection failed"));

        Assertions.assertThrows(DataAccessResourceFailureException.class,
                () -> repository.resolveTimeRange("instance-001"));
        Mockito.verify(jdbcTemplate, Mockito.never())
                .queryForObject(Mockito.contains("MIN(simtime)"), Mockito.eq(Long.class));
    }

    /**
     * 验证所有来源都无数据时抛出明确业务异常。
     */
    @Test
    void shouldRejectWhenTimeRangeDataMissing() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTimeControlRepository repository = new ReplayTimeControlRepository(jdbcTemplate, new ReplayServerProperties());
        Mockito.when(jdbcTemplate.queryForObject(Mockito.anyString(), Mockito.eq(Long.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        Assertions.assertThrows(BusinessException.class, () -> repository.resolveTimeRange("instance-001"));
    }

    /**
     * 验证控制表查询使用清洗后的实例表名和配置化停止消息。
     */
    @Test
    void shouldUseConfiguredStopMessageForEndTime() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayServerProperties properties = new ReplayServerProperties();
        properties.getReplay().getQuery().setStopMessageType(12);
        properties.getReplay().getQuery().setStopMessageCode(34);
        ReplayTimeControlRepository repository = new ReplayTimeControlRepository(jdbcTemplate, properties);
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("WHERE rate > 0"), Mockito.eq(Long.class)))
                .thenReturn(100L);
        Mockito.when(jdbcTemplate.queryForObject(Mockito.contains("WHERE msgtype = ? AND msgcode = ?"),
                        Mockito.eq(Long.class), Mockito.eq(12), Mockito.eq(34)))
                .thenReturn(200L);

        repository.resolveTimeRange("instance-001");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), Mockito.eq(Long.class));
        Assertions.assertTrue(sqlCaptor.getValue().contains("time_control_instance_001"));
    }
}

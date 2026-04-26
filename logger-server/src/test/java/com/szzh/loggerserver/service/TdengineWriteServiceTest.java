package com.szzh.loggerserver.service;

import com.szzh.loggerserver.model.dto.SituationRecordCommand;
import com.szzh.loggerserver.model.dto.TimeControlRecordCommand;
import com.szzh.common.exception.BusinessException;
import com.taosdata.jdbc.ws.TSWSPreparedStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * TDengine 写入服务测试。
 */
class TdengineWriteServiceTest {

    /**
     * 验证标准 JDBC 写入会正确设置参数。
     */
    @Test
    void shouldWriteSingleRecordByPreparedStatement() throws Exception {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        TdengineWriteService writeService = new TdengineWriteService(jdbcTemplate, dataSource, 3);
        SituationRecordCommand command = SituationRecordCommand.builder()
                .instanceId("instance-001")
                .senderId(11)
                .messageType(2100)
                .messageCode(7)
                .simTime(123456L)
                .rawData("payload".getBytes())
                .build();

        writeService.write(command);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).update(sqlCaptor.capture(), Mockito.any(org.springframework.jdbc.core.PreparedStatementSetter.class));
        Assertions.assertTrue(sqlCaptor.getValue().contains("INSERT INTO situation_2100_7_11_instance_001 USING situation_instance_001"));

        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        org.springframework.jdbc.core.PreparedStatementSetter setter =
                Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
                        .filter(invocation -> "update".equals(invocation.getMethod().getName()))
                        .map(invocation -> (org.springframework.jdbc.core.PreparedStatementSetter) invocation.getArgument(1))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("未捕获 PreparedStatementSetter"));
        setter.setValues(preparedStatement);
        Mockito.verify(preparedStatement).setInt(1, 11);
        Mockito.verify(preparedStatement).setInt(2, 2100);
        Mockito.verify(preparedStatement).setInt(3, 7);
        Mockito.verify(preparedStatement).setLong(4, 123456L);
        Mockito.verify(preparedStatement).setBytes(5, command.getRawData());
    }

    /**
     * 验证写入失败时会按次数重试。
     */
    @Test
    void shouldRetryWhenWriteFails() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        TdengineWriteService writeService = new TdengineWriteService(jdbcTemplate, dataSource, 3);
        SituationRecordCommand command = SituationRecordCommand.builder()
                .instanceId("instance-001")
                .senderId(11)
                .messageType(2100)
                .messageCode(7)
                .simTime(123456L)
                .rawData("payload".getBytes())
                .build();

        Mockito.doThrow(new RuntimeException("db error"))
                .when(jdbcTemplate)
                .update(Mockito.anyString(), Mockito.any(org.springframework.jdbc.core.PreparedStatementSetter.class));

        Assertions.assertThrows(RuntimeException.class, () -> writeService.write(command));
        Mockito.verify(jdbcTemplate, Mockito.times(3))
                .update(Mockito.anyString(), Mockito.any(org.springframework.jdbc.core.PreparedStatementSetter.class));
    }

    /**
     * 验证控制时间点写入会正确设置参数顺序。
     */
    @Test
    void shouldWriteTimeControlRecord() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        TdengineWriteService writeService = new TdengineWriteService(jdbcTemplate, dataSource, 3);
        TimeControlRecordCommand command = TimeControlRecordCommand.builder()
                .instanceId("instance-001")
                .senderId(11)
                .messageType(2100)
                .messageCode(7)
                .simTime(123456L)
                .rate(0D)
                .build();

        writeService.writeTimeControl(command);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).update(sqlCaptor.capture(),
                Mockito.eq(123456L),
                Mockito.eq(0D),
                Mockito.eq(11),
                Mockito.eq(2100),
                Mockito.eq(7));
        Assertions.assertEquals("INSERT INTO time_control_instance_001 VALUES (NOW, ?, ?, ?, ?, ?)",
                sqlCaptor.getValue());
    }

    /**
     * 验证控制时间点写入失败时会按次数重试。
     */
    @Test
    void shouldRetryWhenWriteTimeControlFails() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        TdengineWriteService writeService = new TdengineWriteService(jdbcTemplate, dataSource, 2);
        TimeControlRecordCommand command = TimeControlRecordCommand.builder()
                .instanceId("instance-001")
                .senderId(11)
                .messageType(2100)
                .messageCode(7)
                .simTime(123456L)
                .rate(1D)
                .build();

        Mockito.doThrow(new RuntimeException("db error"))
                .when(jdbcTemplate)
                .update(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> writeService.writeTimeControl(command));
        Assertions.assertEquals(BusinessException.Category.TDENGINE_WRITE, exception.getCategory());
        Mockito.verify(jdbcTemplate, Mockito.times(2))
                .update(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    /**
     * 验证批量写入会使用 TSWSPreparedStatement。
     */
    @Test
    void shouldWriteBatchByTaosPreparedStatement() throws Exception {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        TSWSPreparedStatement taosPrepareStatement = Mockito.mock(TSWSPreparedStatement.class);
        TdengineWriteService writeService = new TdengineWriteService(jdbcTemplate, dataSource, 2);
        SituationRecordCommand command = SituationRecordCommand.builder()
                .instanceId("instance-001")
                .senderId(11)
                .messageType(2100)
                .messageCode(7)
                .simTime(123456L)
                .rawData("payload".getBytes())
                .build();

        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.prepareStatement(Mockito.anyString())).thenReturn(taosPrepareStatement);

        writeService.writeBatchByStmt(Collections.singletonList(command));

        Mockito.verify(taosPrepareStatement).setTableName("situation_2100_7_11_instance_001");
        Mockito.verify(taosPrepareStatement).setTagInt(1, 11);
        Mockito.verify(taosPrepareStatement).setTagInt(2, 2100);
        Mockito.verify(taosPrepareStatement).setTagInt(3, 7);
        Mockito.verify(taosPrepareStatement).setTimestamp(1, Collections.singletonList(123456L));
        Mockito.verify(taosPrepareStatement).setVarbinary(2, Arrays.asList(command.getRawData()), command.getRawData().length);
        Mockito.verify(taosPrepareStatement).columnDataAddBatch();
        Mockito.verify(taosPrepareStatement).columnDataExecuteBatch();
        Mockito.verify(taosPrepareStatement).columnDataCloseBatch();
    }

    /**
     * 验证批量写入遇到非 TSWSPreparedStatement 时会失败。
     */
    @Test
    void shouldRejectNonTaosPrepareStatement() throws Exception {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        TdengineWriteService writeService = new TdengineWriteService(jdbcTemplate, dataSource, 2);
        SituationRecordCommand command = SituationRecordCommand.builder()
                .instanceId("instance-001")
                .senderId(11)
                .messageType(2100)
                .messageCode(7)
                .simTime(123456L)
                .rawData("payload".getBytes())
                .build();

        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.prepareStatement(Mockito.anyString())).thenReturn(preparedStatement);

        Assertions.assertThrows(IllegalStateException.class,
                () -> writeService.writeBatchByStmt(Collections.singletonList(command)));
    }
}

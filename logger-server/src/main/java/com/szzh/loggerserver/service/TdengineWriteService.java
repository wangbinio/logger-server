package com.szzh.loggerserver.service;

import com.szzh.loggerserver.config.LoggerServerProperties;
import com.szzh.loggerserver.model.dto.SituationRecordCommand;
import com.szzh.loggerserver.model.dto.TimeControlRecordCommand;
import com.szzh.common.exception.BusinessException;
import com.szzh.loggerserver.support.constant.TdengineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TDengine 写入服务。
 */
@Service
public class TdengineWriteService {

    private static final Logger log = LoggerFactory.getLogger(TdengineWriteService.class);

    private final JdbcTemplate jdbcTemplate;

    private final DataSource dataSource;

    private final int retryTimes;

    /**
     * 创建 TDengine 写入服务。
     *
     * @param jdbcTemplate JDBC 模板。
     * @param dataSource 数据源。
     * @param properties 项目配置。
     */
    @Autowired
    public TdengineWriteService(JdbcTemplate jdbcTemplate,
                                DataSource dataSource,
                                LoggerServerProperties properties) {
        this(jdbcTemplate, dataSource, properties.getWrite().getRetryTimes());
    }

    /**
     * 创建 TDengine 写入服务。
     *
     * @param jdbcTemplate JDBC 模板。
     * @param dataSource 数据源。
     * @param retryTimes 重试次数。
     */
    public TdengineWriteService(JdbcTemplate jdbcTemplate, DataSource dataSource, int retryTimes) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource 不能为空");
        this.retryTimes = Math.max(1, retryTimes);
    }

    /**
     * 使用标准 JDBC 写入单条数据。
     *
     * @param command 写入命令。
     */
    public void write(SituationRecordCommand command) {
        SituationRecordCommand validatedCommand = requireCommand(command);
        String sql = TdengineConstants.buildInsertUsingSql(
                validatedCommand.getInstanceId(),
                validatedCommand.getMessageType(),
                validatedCommand.getMessageCode(),
                validatedCommand.getSenderId());

        RuntimeException lastException = null;
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            try {
                jdbcTemplate.update(sql, createPreparedStatementSetter(validatedCommand));
                return;
            } catch (RuntimeException exception) {
                lastException = exception;

                logWriteRetryFailed(validatedCommand, attempt + 1, exception);
            }
        }
        throw BusinessException.tdengineWrite("TDengine 单条写入失败", lastException);
    }

    /**
     * 写入仿真控制时间点。
     *
     * @param command 控制时间点写入命令。
     */
    public void writeTimeControl(TimeControlRecordCommand command) {
        TimeControlRecordCommand validatedCommand = requireTimeControlCommand(command);
        String sql = TdengineConstants.buildInsertTimeControlSql(validatedCommand.getInstanceId());

        RuntimeException lastException = null;
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            try {
                jdbcTemplate.update(sql,
                        validatedCommand.getSimTime(),
                        validatedCommand.getRate(),
                        validatedCommand.getSenderId(),
                        validatedCommand.getMessageType(),
                        validatedCommand.getMessageCode());
                return;
            } catch (RuntimeException exception) {
                lastException = exception;

                logTimeControlWriteRetryFailed(validatedCommand, attempt + 1, exception);
            }
        }
        throw BusinessException.tdengineWrite("TDengine 控制时间点写入失败", lastException);
    }

    /**
     * 使用标准 JDBC PreparedStatement 批量写入。
     *
     * @param commands 写入命令集合。
     */
    public void writeBatchByStmt(List<SituationRecordCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        Map<String, List<SituationRecordCommand>> commandsBySql = groupCommandsBySql(commands);
        try (Connection connection = dataSource.getConnection()) {
            for (Map.Entry<String, List<SituationRecordCommand>> entry : commandsBySql.entrySet()) {
                executeBatch(connection, entry.getKey(), entry.getValue());
            }
        } catch (SQLException exception) {
            throw BusinessException.tdengineWrite("TDengine 标准 JDBC 批量写入失败", exception);
        }
    }

    /**
     * 创建标准 JDBC 参数设置器。
     *
     * @param command 写入命令。
     * @return 参数设置器。
     */
    private PreparedStatementSetter createPreparedStatementSetter(SituationRecordCommand command) {
        return preparedStatement -> {
            preparedStatement.setInt(1, command.getSenderId());
            preparedStatement.setInt(2, command.getMessageType());
            preparedStatement.setInt(3, command.getMessageCode());
            preparedStatement.setLong(4, command.getSimTime());
            preparedStatement.setBytes(5, command.getRawData());
        };
    }

    /**
     * 按 SQL 分组写入命令。
     *
     * @param commands 原始写入命令集合。
     * @return SQL 与命令集合映射。
     */
    private Map<String, List<SituationRecordCommand>> groupCommandsBySql(List<SituationRecordCommand> commands) {
        Map<String, List<SituationRecordCommand>> commandsBySql =
                new LinkedHashMap<String, List<SituationRecordCommand>>();
        for (SituationRecordCommand command : commands) {
            SituationRecordCommand validatedCommand = requireCommand(command);
            String sql = TdengineConstants.buildInsertUsingSql(
                    validatedCommand.getInstanceId(),
                    validatedCommand.getMessageType(),
                    validatedCommand.getMessageCode(),
                    validatedCommand.getSenderId());
            if (!commandsBySql.containsKey(sql)) {
                commandsBySql.put(sql, new ArrayList<SituationRecordCommand>());
            }
            commandsBySql.get(sql).add(validatedCommand);
        }
        return commandsBySql;
    }

    /**
     * 执行同一 SQL 下的批量写入。
     *
     * @param connection 数据库连接。
     * @param sql 写入 SQL。
     * @param commands 写入命令集合。
     * @throws SQLException SQL 异常。
     */
    private void executeBatch(Connection connection,
                              String sql,
                              List<SituationRecordCommand> commands) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (SituationRecordCommand command : commands) {
                applyBatchParameters(preparedStatement, command);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        }
    }

    /**
     * 设置标准 JDBC 批量写入参数。
     *
     * @param preparedStatement 预编译语句。
     * @param command 写入命令。
     * @throws SQLException SQL 异常。
     */
    private void applyBatchParameters(PreparedStatement preparedStatement,
                                      SituationRecordCommand command) throws SQLException {
        preparedStatement.setInt(1, command.getSenderId());
        preparedStatement.setInt(2, command.getMessageType());
        preparedStatement.setInt(3, command.getMessageCode());
        preparedStatement.setLong(4, command.getSimTime());
        preparedStatement.setBytes(5, command.getRawData());
    }

    /**
     * 校验写入命令。
     *
     * @param command 写入命令。
     * @return 原始写入命令。
     */
    private SituationRecordCommand requireCommand(SituationRecordCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("写入命令不能为空");
        }
        return command;
    }

    /**
     * 校验控制时间点写入命令。
     *
     * @param command 控制时间点写入命令。
     * @return 原始写入命令。
     */
    private TimeControlRecordCommand requireTimeControlCommand(TimeControlRecordCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("控制时间点写入命令不能为空");
        }
        return command;
    }

    /**
     * 输出态势记录重试失败日志。
     *
     * @param command 写入命令。
     * @param attempt 重试序号。
     * @param exception 写入异常。
     */
    private void logWriteRetryFailed(SituationRecordCommand command, int attempt, RuntimeException exception) {
        log.warn("result=write_retry_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} costMs=-1 attempt={} reason={}",
                command.getInstanceId(), command.getMessageType(), command.getMessageCode(), command.getSenderId(), command.getSimTime(), attempt, exception.getMessage());
    }

    /**
     * 输出控制时间点重试失败日志。
     *
     * @param command 控制时间点写入命令。
     * @param attempt 重试序号。
     * @param exception 写入异常。
     */
    private void logTimeControlWriteRetryFailed(TimeControlRecordCommand command,
                                                int attempt,
                                                RuntimeException exception) {
        log.warn("result=time_control_write_retry_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} rate={} attempt={} reason={}",
                command.getInstanceId(), command.getMessageType(), command.getMessageCode(), command.getSenderId(), command.getSimTime(), command.getRate(), attempt, exception.getMessage());
    }
}

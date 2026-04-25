package com.szzh.loggerserver.service;

import com.szzh.loggerserver.config.LoggerServerProperties;
import com.szzh.loggerserver.model.dto.SituationRecordCommand;
import com.szzh.loggerserver.support.exception.BusinessException;
import com.szzh.loggerserver.support.constant.TdengineConstants;
import com.taosdata.jdbc.ws.TSWSPreparedStatement;
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
import java.util.Collections;
import java.util.List;
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
                log.warn("result=write_retry_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} costMs=-1 attempt={} reason={}",
                        validatedCommand.getInstanceId(),
                        validatedCommand.getMessageType(),
                        validatedCommand.getMessageCode(),
                        validatedCommand.getSenderId(),
                        validatedCommand.getSimTime(),
                        attempt + 1,
                        exception.getMessage());
            }
        }
        throw BusinessException.tdengineWrite("TDengine 单条写入失败", lastException);
    }

    /**
     * 使用 TaosPrepareStatement 批量写入。
     *
     * @param commands 写入命令集合。
     */
    public void writeBatchByStmt(List<SituationRecordCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement =
                     connection.prepareStatement(TdengineConstants.buildInsertStmtSql(commands.get(0).getInstanceId()))) {
            if (!(preparedStatement instanceof TSWSPreparedStatement)) {
                throw new IllegalStateException("当前驱动未返回 TSWSPreparedStatement");
            }
            TSWSPreparedStatement taosPrepareStatement = (TSWSPreparedStatement) preparedStatement;
            for (SituationRecordCommand command : commands) {
                SituationRecordCommand validatedCommand = requireCommand(command);
                applyStmtParameters(taosPrepareStatement, validatedCommand);
            }
            taosPrepareStatement.columnDataExecuteBatch();
            taosPrepareStatement.columnDataCloseBatch();
        } catch (SQLException exception) {
            throw BusinessException.tdengineWrite("TDengine stmt 批量写入失败", exception);
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
     * 设置 stmt 批量写入参数。
     *
     * @param taosPrepareStatement TDengine 预编译语句。
     * @param command 写入命令。
     * @throws SQLException SQL 异常。
     */
    private void applyStmtParameters(TSWSPreparedStatement taosPrepareStatement,
                                     SituationRecordCommand command) throws SQLException {
        taosPrepareStatement.setTableName(TdengineConstants.buildSubTableName(
                command.getInstanceId(),
                command.getMessageType(),
                command.getMessageCode(),
                command.getSenderId()));
        taosPrepareStatement.setTagInt(1, command.getSenderId());
        taosPrepareStatement.setTagInt(2, command.getMessageType());
        taosPrepareStatement.setTagInt(3, command.getMessageCode());
        taosPrepareStatement.setTimestamp(1, Collections.singletonList(command.getSimTime()));
        taosPrepareStatement.setVarbinary(2,
                Collections.singletonList(command.getRawData()),
                command.getRawData().length);
        // WebSocket stmt 批量写入需要先累积列数据，再统一执行。
        taosPrepareStatement.columnDataAddBatch();
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
}

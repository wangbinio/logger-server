package com.szzh.replayserver.repository;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.tdengine.TdengineNaming;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * 回放控制时间点查询 Repository。
 */
@Repository
public class ReplayTimeControlRepository {

    private final JdbcTemplate jdbcTemplate;

    private final int stopMessageType;

    private final int stopMessageCode;

    /**
     * 创建回放控制时间点查询 Repository。
     *
     * @param jdbcTemplate JDBC 模板。
     * @param properties 回放服务配置。
     */
    public ReplayTimeControlRepository(JdbcTemplate jdbcTemplate, ReplayServerProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
        ReplayServerProperties.Query query = Objects.requireNonNull(properties, "properties 不能为空")
                .getReplay().getQuery();
        this.stopMessageType = query.getStopMessageType();
        this.stopMessageCode = query.getStopMessageCode();
    }

    /**
     * 解析指定实例的回放时间范围。
     *
     * @param instanceId 实例 ID。
     * @return 回放时间范围。
     */
    public ReplayTimeRange resolveTimeRange(String instanceId) {
        String timeControlTableName = TdengineNaming.buildTimeControlTableName(instanceId);
        String stableName = TdengineNaming.buildStableName(instanceId);
        Long startTime = queryStartTime(timeControlTableName);
        if (startTime == null) {
            startTime = querySituationMinTime(stableName);
        }
        if (startTime == null) {
            throw BusinessException.state("无法计算回放开始时间: " + instanceId);
        }

        Long endTime = queryStopTime(timeControlTableName);
        if (endTime == null) {
            endTime = querySituationMaxTime(stableName);
        }
        if (endTime == null) {
            throw BusinessException.state("无法计算回放结束时间: " + instanceId);
        }
        if (endTime < startTime) {
            throw BusinessException.state("回放结束时间小于开始时间: " + instanceId);
        }
        return new ReplayTimeRange(startTime, endTime);
    }

    /**
     * 查询控制表中的开始时间。
     *
     * @param tableName 控制时间点表名。
     * @return 开始时间。
     */
    private Long queryStartTime(String tableName) {
        String sql = "SELECT simtime FROM " + tableName
                + " WHERE rate > 0 ORDER BY simtime ASC LIMIT 1";
        return queryLong(sql);
    }

    /**
     * 查询态势表中的最小仿真时间。
     *
     * @param stableName 态势超级表名。
     * @return 最小仿真时间。
     */
    private Long querySituationMinTime(String stableName) {
        String sql = "SELECT MIN(simtime) FROM " + stableName;
        return queryLong(sql);
    }

    /**
     * 查询控制表中的停止时间。
     *
     * @param tableName 控制时间点表名。
     * @return 停止时间。
     */
    private Long queryStopTime(String tableName) {
        String sql = "SELECT simtime FROM " + tableName
                + " WHERE msgtype = ? AND msgcode = ? ORDER BY simtime DESC LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, stopMessageType, stopMessageCode);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    /**
     * 查询态势表中的最大仿真时间。
     *
     * @param stableName 态势超级表名。
     * @return 最大仿真时间。
     */
    private Long querySituationMaxTime(String stableName) {
        String sql = "SELECT MAX(simtime) FROM " + stableName;
        return queryLong(sql);
    }

    /**
     * 查询单个 Long 值，空结果转为 null。
     *
     * @param sql 查询 SQL。
     * @return Long 值或 null。
     */
    private Long queryLong(String sql) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}

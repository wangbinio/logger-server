package com.szzh.replayserver.repository;

import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 回放帧查询 Repository。
 */
@Repository
public class ReplayFrameRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建回放帧查询 Repository。
     *
     * @param jdbcTemplate JDBC 模板。
     */
    public ReplayFrameRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
    }

    /**
     * 查询连续回放窗口内的数据帧。
     *
     * @param tableDescriptor 子表描述。
     * @param fromExclusive 左开仿真时间。
     * @param toInclusive 右闭仿真时间。
     * @param cursor 分页游标。
     * @return 数据帧列表。
     */
    public List<ReplayFrame> findWindowFrames(ReplayTableDescriptor tableDescriptor,
                                              long fromExclusive,
                                              long toInclusive,
                                              ReplayCursor cursor) {
        String sql = buildPagedFrameSql(tableDescriptor.getTableName(), "simtime > ? AND simtime <= ?");
        return queryFrames(tableDescriptor, sql, fromExclusive, toInclusive, cursor.getLimit(), cursor.getOffset());
    }

    /**
     * 查询向后跳转需要补发的事件帧。
     *
     * @param tableDescriptor 子表描述。
     * @param startInclusive 起始仿真时间。
     * @param targetInclusive 目标仿真时间。
     * @param cursor 分页游标。
     * @return 数据帧列表。
     */
    public List<ReplayFrame> findBackwardJumpEventFrames(ReplayTableDescriptor tableDescriptor,
                                                         long startInclusive,
                                                         long targetInclusive,
                                                         ReplayCursor cursor) {
        String sql = buildPagedFrameSql(tableDescriptor.getTableName(), "simtime >= ? AND simtime <= ?");
        return queryFrames(tableDescriptor, sql, startInclusive, targetInclusive, cursor.getLimit(), cursor.getOffset());
    }

    /**
     * 查询向前跳转需要补发的事件帧。
     *
     * @param tableDescriptor 子表描述。
     * @param currentExclusive 当前仿真时间。
     * @param targetInclusive 目标仿真时间。
     * @param cursor 分页游标。
     * @return 数据帧列表。
     */
    public List<ReplayFrame> findForwardJumpEventFrames(ReplayTableDescriptor tableDescriptor,
                                                        long currentExclusive,
                                                        long targetInclusive,
                                                        ReplayCursor cursor) {
        String sql = buildPagedFrameSql(tableDescriptor.getTableName(), "simtime > ? AND simtime <= ?");
        return queryFrames(tableDescriptor, sql, currentExclusive, targetInclusive, cursor.getLimit(), cursor.getOffset());
    }

    /**
     * 查询周期表在目标时间前的最后一帧。
     *
     * @param tableDescriptor 子表描述。
     * @param targetInclusive 目标仿真时间。
     * @return 最后一帧。
     */
    public Optional<ReplayFrame> findPeriodicLastFrame(ReplayTableDescriptor tableDescriptor, long targetInclusive) {
        String sql = "SELECT simtime, rawdata FROM " + tableDescriptor.getTableName()
                + " WHERE simtime <= ? ORDER BY simtime DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, targetInclusive);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toFrame(tableDescriptor, rows.get(0)));
    }

    /**
     * 构建分页帧查询 SQL。
     *
     * @param tableName 子表名。
     * @param whereClause 时间条件。
     * @return 查询 SQL。
     */
    private String buildPagedFrameSql(String tableName, String whereClause) {
        return "SELECT simtime, rawdata FROM " + tableName
                + " WHERE " + whereClause
                + " ORDER BY simtime ASC LIMIT ? OFFSET ?";
    }

    /**
     * 查询并转换分页帧。
     *
     * @param tableDescriptor 子表描述。
     * @param sql 查询 SQL。
     * @param lowerBound 下界时间。
     * @param upperBound 上界时间。
     * @param limit 分页大小。
     * @param offset 分页偏移。
     * @return 数据帧列表。
     */
    private List<ReplayFrame> queryFrames(ReplayTableDescriptor tableDescriptor,
                                          String sql,
                                          long lowerBound,
                                          long upperBound,
                                          int limit,
                                          int offset) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, lowerBound, upperBound, limit, offset);
        List<ReplayFrame> frames = new ArrayList<ReplayFrame>();
        for (Map<String, Object> row : rows) {
            frames.add(toFrame(tableDescriptor, row));
        }
        return frames;
    }

    /**
     * 将查询结果行转换为回放帧。
     *
     * @param tableDescriptor 子表描述。
     * @param row 查询结果行。
     * @return 回放帧。
     */
    private ReplayFrame toFrame(ReplayTableDescriptor tableDescriptor, Map<String, Object> row) {
        return new ReplayFrame(
                tableDescriptor.getTableName(),
                tableDescriptor.getSenderId(),
                tableDescriptor.getMessageType(),
                tableDescriptor.getMessageCode(),
                toLong(row.get("simtime")),
                toBytes(row.get("rawdata")));
    }

    /**
     * 转换查询结果为 long。
     *
     * @param value 查询值。
     * @return long 值。
     */
    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(Objects.requireNonNull(value, "simtime 不能为空").toString());
    }

    /**
     * 转换查询结果为字节数组。
     *
     * @param value 查询值。
     * @return 字节数组。
     */
    private byte[] toBytes(Object value) {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        throw new IllegalStateException("rawdata 必须为 byte[]");
    }
}

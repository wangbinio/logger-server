package com.szzh.replayserver.repository;

import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.util.List;
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
        List<ReplayFrame> frames = jdbcTemplate.query(sql, createFrameRowMapper(tableDescriptor), targetInclusive);
        if (frames.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(frames.get(0));
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
        return jdbcTemplate.query(sql, createFrameRowMapper(tableDescriptor), lowerBound, upperBound, limit, offset);
    }

    /**
     * 创建回放帧结果映射器。
     *
     * @param tableDescriptor 子表描述。
     * @return 回放帧结果映射器。
     */
    private RowMapper<ReplayFrame> createFrameRowMapper(ReplayTableDescriptor tableDescriptor) {
        return new RowMapper<ReplayFrame>() {
            /**
             * 将 JDBC ResultSet 当前行转换为回放帧。
             *
             * @param resultSet 查询结果集。
             * @param rowNum 行号。
             * @return 回放帧。
             * @throws SQLException SQL 异常。
             */
            @Override
            public ReplayFrame mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                return new ReplayFrame(
                        tableDescriptor.getTableName(),
                        tableDescriptor.getSenderId(),
                        tableDescriptor.getMessageType(),
                        tableDescriptor.getMessageCode(),
                        resultSet.getLong("simtime"),
                        readRawData(resultSet));
            }
        };
    }

    /**
     * 从结果集中读取二进制 rawdata。
     *
     * @param resultSet 查询结果集。
     * @return 原始载荷。
     * @throws SQLException SQL 异常。
     */
    private byte[] readRawData(ResultSet resultSet) throws SQLException {
        byte[] rawData = resultSet.getBytes("rawdata");
        if (rawData != null) {
            return rawData;
        }
        return convertRawData(resultSet.getObject("rawdata"));
    }

    /**
     * 兼容低版本 RESTful 驱动返回的 rawdata 对象类型。
     *
     * @param value rawdata 查询值。
     * @return 原始载荷。
     * @throws SQLException SQL 异常。
     */
    private byte[] convertRawData(Object value) throws SQLException {
        if (value == null) {
            throw new IllegalStateException("rawdata 不能为空");
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof String) {
            return ((String) value).getBytes(StandardCharsets.UTF_8);
        }
        if (value instanceof Blob) {
            Blob blob = (Blob) value;
            return blob.getBytes(1L, (int) blob.length());
        }
        if (value instanceof ByteBuffer) {
            ByteBuffer byteBuffer = ((ByteBuffer) value).slice();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            return bytes;
        }
        throw new IllegalStateException("不支持的 rawdata 类型: " + value.getClass().getName());
    }
}

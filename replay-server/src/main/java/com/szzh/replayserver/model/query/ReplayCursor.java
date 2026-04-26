package com.szzh.replayserver.model.query;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 回放分页游标。
 */
@Getter
@ToString
@EqualsAndHashCode
public class ReplayCursor {

    private final String tableName;

    private final int limit;

    private final int offset;

    /**
     * 创建回放分页游标。
     *
     * @param tableName 子表名。
     * @param limit 分页大小。
     * @param offset 分页偏移。
     */
    public ReplayCursor(String tableName, int limit, int offset) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName 不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset 不能小于 0");
        }
        this.tableName = tableName.trim();
        this.limit = limit;
        this.offset = offset;
    }

    /**
     * 按已消费数量推进游标。
     *
     * @param consumedCount 已消费数量。
     * @return 推进后的游标。
     */
    public ReplayCursor advance(int consumedCount) {
        if (consumedCount < 0) {
            throw new IllegalArgumentException("consumedCount 不能小于 0");
        }
        return new ReplayCursor(tableName, limit, offset + consumedCount);
    }
}

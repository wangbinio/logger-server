package com.szzh.replayserver.model.query;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 回放仿真时间范围。
 */
@Getter
@ToString
@EqualsAndHashCode
public class ReplayTimeRange {

    private final long startTime;

    private final long endTime;

    private final long duration;

    /**
     * 创建回放仿真时间范围。
     *
     * @param startTime 起始仿真时间。
     * @param endTime 结束仿真时间。
     */
    public ReplayTimeRange(long startTime, long endTime) {
        if (endTime < startTime) {
            throw new IllegalArgumentException("结束时间不能小于开始时间");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = endTime - startTime;
    }
}

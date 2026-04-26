package com.szzh.replayserver.model.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 回放元信息 payload。
 */
@Getter
@ToString
@EqualsAndHashCode
public class ReplayMetadataPayload {

    private final long startTime;

    private final long endTime;

    private final long duration;

    /**
     * 创建回放元信息 payload。
     *
     * @param startTime 起始仿真时间。
     * @param endTime 结束仿真时间。
     */
    public ReplayMetadataPayload(long startTime, long endTime) {
        if (endTime < startTime) {
            throw new IllegalArgumentException("结束时间不能小于开始时间");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = endTime - startTime;
    }
}

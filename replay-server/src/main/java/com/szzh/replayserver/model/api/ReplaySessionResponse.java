package com.szzh.replayserver.model.api;

import com.szzh.replayserver.domain.session.ReplaySession;
import lombok.Getter;

/**
 * 回放会话 HTTP 响应快照。
 */
@Getter
public class ReplaySessionResponse {

    private final String instanceId;

    private final long startTime;

    private final long endTime;

    private final long duration;

    private final String state;

    private final double rate;

    private final long currentReplayTime;

    private final long lastDispatchedSimTime;

    /**
     * 创建回放会话 HTTP 响应快照。
     *
     * @param instanceId 实例 ID。
     * @param startTime 起始仿真时间。
     * @param endTime 结束仿真时间。
     * @param duration 持续时间。
     * @param state 当前状态。
     * @param rate 当前倍率。
     * @param currentReplayTime 当前回放时间。
     * @param lastDispatchedSimTime 已发布水位。
     */
    public ReplaySessionResponse(String instanceId,
                                 long startTime,
                                 long endTime,
                                 long duration,
                                 String state,
                                 double rate,
                                 long currentReplayTime,
                                 long lastDispatchedSimTime) {
        this.instanceId = instanceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.state = state;
        this.rate = rate;
        this.currentReplayTime = currentReplayTime;
        this.lastDispatchedSimTime = lastDispatchedSimTime;
    }

    /**
     * 从回放会话创建 HTTP 响应快照。
     *
     * @param session 回放会话。
     * @return HTTP 响应快照。
     */
    public static ReplaySessionResponse fromSession(ReplaySession session) {
        if (session == null) {
            return null;
        }
        return new ReplaySessionResponse(
                session.getInstanceId(),
                session.getSimulationStartTime(),
                session.getSimulationEndTime(),
                session.getDuration(),
                session.getState().name(),
                session.getRate(),
                session.getReplayClock().currentTime(),
                session.getLastDispatchedSimTime());
    }
}

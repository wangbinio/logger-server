package com.szzh.replayserver.service;

import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.dto.ReplayJumpPayload;
import com.szzh.replayserver.model.dto.ReplayRatePayload;
import com.szzh.replayserver.mq.ReplayControlCommandPort;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 回放控制服务。
 */
@Service
public class ReplayControlService implements ReplayControlCommandPort {

    private static final Logger log = LoggerFactory.getLogger(ReplayControlService.class);

    private final ReplaySessionManager sessionManager;

    private final ReplayScheduler scheduler;

    private final ReplayJumpService jumpService;

    private final ReplayMetrics replayMetrics;

    /**
     * 创建回放控制服务。
     *
     * @param sessionManager 回放会话管理器。
     * @param scheduler 回放调度器。
     * @param jumpService 回放跳转服务。
     */
    public ReplayControlService(ReplaySessionManager sessionManager,
                                ReplayScheduler scheduler,
                                ReplayJumpService jumpService) {
        this(sessionManager, scheduler, jumpService, new ReplayMetrics());
    }

    /**
     * 创建回放控制服务。
     *
     * @param sessionManager 回放会话管理器。
     * @param scheduler 回放调度器。
     * @param jumpService 回放跳转服务。
     * @param replayMetrics 回放指标。
     */
    @Autowired
    public ReplayControlService(ReplaySessionManager sessionManager,
                                ReplayScheduler scheduler,
                                ReplayJumpService jumpService,
                                ReplayMetrics replayMetrics) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler 不能为空");
        this.jumpService = Objects.requireNonNull(jumpService, "jumpService 不能为空");
        this.replayMetrics = Objects.requireNonNull(replayMetrics, "replayMetrics 不能为空");
    }

    /**
     * 处理启动回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handleStart(String instanceId, ProtocolData protocolData) {
        Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            replayMetrics.recordStateConflict();
            return;
        }
        ReplaySession session = sessionOptional.get();
        boolean shouldSchedule = false;
        synchronized (session) {
            if (session.getState() == ReplaySessionState.READY) {
                session.start();
                shouldSchedule = true;
            } else if (session.getState() == ReplaySessionState.PAUSED) {
                session.resume();
                shouldSchedule = true;
            } else {
                replayMetrics.recordStateConflict();
            }
        }
        if (shouldSchedule) {
            // 启动或恢复连续回放调度。
            scheduler.schedule(session);

            logControlResult("replay_start_success", instanceId, protocolData, session);
        }
    }

    /**
     * 处理暂停回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handlePause(String instanceId, ProtocolData protocolData) {
        Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            replayMetrics.recordStateConflict();
            return;
        }
        ReplaySession session = sessionOptional.get();
        boolean shouldCancel = false;
        synchronized (session) {
            if (session.getState() == ReplaySessionState.RUNNING) {
                session.pause();
                shouldCancel = true;
            } else {
                replayMetrics.recordStateConflict();
            }
        }
        if (shouldCancel) {
            // 暂停连续回放调度，保留会话和控制订阅。
            scheduler.cancel(instanceId);

            logControlResult("replay_pause_success", instanceId, protocolData, session);
        }
    }

    /**
     * 处理继续回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handleResume(String instanceId, ProtocolData protocolData) {
        Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            replayMetrics.recordStateConflict();
            return;
        }
        ReplaySession session = sessionOptional.get();
        boolean shouldSchedule = false;
        synchronized (session) {
            if (session.getState() == ReplaySessionState.PAUSED) {
                session.resume();
                shouldSchedule = true;
            } else {
                replayMetrics.recordStateConflict();
            }
        }
        if (shouldSchedule) {
            // 从暂停时间继续连续回放调度。
            scheduler.schedule(session);

            logControlResult("replay_resume_success", instanceId, protocolData, session);
        }
    }

    /**
     * 处理倍速回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handleRate(String instanceId, ProtocolData protocolData) {
        ReplayRatePayload payload;
        try {
            payload = ReplayRatePayload.fromRawData(requireProtocolData(protocolData).getRawData());
        } catch (IllegalArgumentException exception) {
            log.debug("result=replay_rate_payload_invalid instanceId={} reason={}", instanceId, exception.getMessage());
            replayMetrics.recordStateConflict();
            return;
        }

        Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            replayMetrics.recordStateConflict();
            return;
        }
        ReplaySession session = sessionOptional.get();
        synchronized (session) {
            if (session.getState() == ReplaySessionState.RUNNING
                    || session.getState() == ReplaySessionState.PAUSED) {
                session.updateRate(payload.getRate());

                logControlResult("replay_rate_success", instanceId, protocolData, session);
            } else {
                replayMetrics.recordStateConflict();
            }
        }
    }

    /**
     * 处理时间跳转命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handleJump(String instanceId, ProtocolData protocolData) {
        ReplayJumpPayload payload;
        try {
            payload = ReplayJumpPayload.fromRawData(requireProtocolData(protocolData).getRawData());
        } catch (IllegalArgumentException exception) {
            log.debug("result=replay_jump_payload_invalid instanceId={} reason={}", instanceId, exception.getMessage());
            replayMetrics.recordStateConflict();
            return;
        }

        Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            replayMetrics.recordStateConflict();
            return;
        }
        ReplaySession session = sessionOptional.get();
        boolean wasRunning = session.getState() == ReplaySessionState.RUNNING;
        if (!isJumpAccepted(session.getState())) {
            replayMetrics.recordStateConflict();
            return;
        }
        if (wasRunning) {
            // 跳转期间暂停连续调度，避免窗口推进和补偿发布并发。
            scheduler.cancel(instanceId);
        }
        try {
            jumpService.jump(session, payload.getTime());

            logControlResult("replay_jump_success", instanceId, protocolData, session);
        } finally {
            if (wasRunning && session.getState() == ReplaySessionState.RUNNING) {
                // 跳转完成后恢复原运行态调度。
                scheduler.schedule(session);
            }
        }
    }

    /**
     * 判断当前状态是否接受时间跳转。
     *
     * @param state 会话状态。
     * @return 是否接受。
     */
    private boolean isJumpAccepted(ReplaySessionState state) {
        return state == ReplaySessionState.READY
                || state == ReplaySessionState.RUNNING
                || state == ReplaySessionState.PAUSED
                || state == ReplaySessionState.COMPLETED;
    }

    /**
     * 校验协议数据。
     *
     * @param protocolData 协议数据。
     * @return 原始协议数据。
     */
    private ProtocolData requireProtocolData(ProtocolData protocolData) {
        return Objects.requireNonNull(protocolData, "protocolData 不能为空");
    }

    /**
     * 输出回放控制结果日志。
     *
     * @param result 处理结果。
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     * @param session 回放会话。
     */
    private void logControlResult(String result,
                                  String instanceId,
                                  ProtocolData protocolData,
                                  ReplaySession session) {
        log.info("result={} instanceId={} topic={} messageType={} messageCode={} senderId={} currentReplayTime={} lastDispatchedSimTime={} rate={} replayState={}",
                result, instanceId, TopicConstants.buildInstanceBroadcastTopic(instanceId), protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), session.getReplayClock().currentTime(), session.getLastDispatchedSimTime(), session.getRate(), session.getState());
    }
}

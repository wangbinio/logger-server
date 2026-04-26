package com.szzh.replayserver.service;

import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.mq.ReplaySituationPublisher;
import com.szzh.replayserver.repository.ReplayFrameRepository;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 回放时间跳转服务。
 */
@Service
public class ReplayJumpService {

    private final ReplayFrameRepository frameRepository;

    private final ReplayFrameMergeService frameMergeService;

    private final ReplaySituationPublisher situationPublisher;

    private final ReplayMetrics replayMetrics;

    private final int pageSize;

    /**
     * 创建回放时间跳转服务。
     *
     * @param frameRepository 回放帧 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param properties 回放服务配置。
     * @param replayMetrics 回放指标。
     */
    @Autowired
    public ReplayJumpService(ReplayFrameRepository frameRepository,
                             ReplayFrameMergeService frameMergeService,
                             ReplaySituationPublisher situationPublisher,
                             ReplayServerProperties properties,
                             ReplayMetrics replayMetrics) {
        this(frameRepository,
                frameMergeService,
                situationPublisher,
                properties.getReplay().getQuery().getPageSize(),
                replayMetrics);
    }

    /**
     * 创建回放时间跳转服务。
     *
     * @param frameRepository 回放帧 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param pageSize 查询分页大小。
     */
    public ReplayJumpService(ReplayFrameRepository frameRepository,
                             ReplayFrameMergeService frameMergeService,
                             ReplaySituationPublisher situationPublisher,
                             int pageSize) {
        this(frameRepository, frameMergeService, situationPublisher, pageSize, new ReplayMetrics());
    }

    /**
     * 创建回放时间跳转服务。
     *
     * @param frameRepository 回放帧 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param pageSize 查询分页大小。
     * @param replayMetrics 回放指标。
     */
    public ReplayJumpService(ReplayFrameRepository frameRepository,
                             ReplayFrameMergeService frameMergeService,
                             ReplaySituationPublisher situationPublisher,
                             int pageSize,
                             ReplayMetrics replayMetrics) {
        this.frameRepository = Objects.requireNonNull(frameRepository, "frameRepository 不能为空");
        this.frameMergeService = Objects.requireNonNull(frameMergeService, "frameMergeService 不能为空");
        this.situationPublisher = Objects.requireNonNull(situationPublisher, "situationPublisher 不能为空");
        this.replayMetrics = Objects.requireNonNull(replayMetrics, "replayMetrics 不能为空");
        this.pageSize = Math.max(1, pageSize);
    }

    /**
     * 执行时间跳转。
     *
     * @param session 回放会话。
     * @param requestedTime 请求目标时间。
     */
    public void jump(ReplaySession session, long requestedTime) {
        ReplaySession replaySession = Objects.requireNonNull(session, "session 不能为空");
        synchronized (replaySession) {
            if (!isJumpAccepted(replaySession.getState())) {
                replayMetrics.recordStateConflict();
                return;
            }
            long currentTime = replaySession.getReplayClock().currentTime();
            long targetTime = clamp(replaySession, requestedTime);
            boolean wasRunning = replaySession.getState() == ReplaySessionState.RUNNING;
            if (wasRunning) {
                replaySession.pause();
            }
            try {
                publishJumpEvents(replaySession, currentTime, targetTime);
                publishPeriodicSnapshots(replaySession, targetTime);
                replaySession.jumpTo(targetTime);
                replaySession.syncLastDispatchedSimTime(targetTime);
                replayMetrics.recordJump();
                if (wasRunning) {
                    replaySession.resume();
                }
            } catch (RuntimeException exception) {
                if (wasRunning && replaySession.getState() == ReplaySessionState.PAUSED) {
                    replaySession.resume();
                }
                throw exception;
            }
        }
    }

    /**
     * 按跳转方向发布事件补偿数据。
     *
     * @param session 回放会话。
     * @param currentTime 当前回放时间。
     * @param targetTime 目标回放时间。
     */
    private void publishJumpEvents(ReplaySession session, long currentTime, long targetTime) {
        if (targetTime < currentTime) {
            publishFrames(session, querySafely(() -> queryBackwardEventFrames(session, targetTime)));
        } else if (targetTime > currentTime) {
            publishFrames(session, querySafely(() -> queryForwardEventFrames(session, currentTime, targetTime)));
        }
    }

    /**
     * 查询向后跳转事件帧。
     *
     * @param session 回放会话。
     * @param targetTime 目标回放时间。
     * @return 已排序事件帧。
     */
    private List<ReplayFrame> queryBackwardEventFrames(ReplaySession session, long targetTime) {
        List<List<ReplayFrame>> framePages = new ArrayList<List<ReplayFrame>>();
        for (ReplayTableDescriptor table : session.getEventTables()) {
            framePages.add(queryBackwardEventTable(session, table, targetTime));
        }
        return frameMergeService.merge(framePages);
    }

    /**
     * 查询向前跳转事件帧。
     *
     * @param session 回放会话。
     * @param currentTime 当前回放时间。
     * @param targetTime 目标回放时间。
     * @return 已排序事件帧。
     */
    private List<ReplayFrame> queryForwardEventFrames(ReplaySession session, long currentTime, long targetTime) {
        List<List<ReplayFrame>> framePages = new ArrayList<List<ReplayFrame>>();
        for (ReplayTableDescriptor table : session.getEventTables()) {
            framePages.add(queryForwardEventTable(table, currentTime, targetTime));
        }
        return frameMergeService.merge(framePages);
    }

    /**
     * 分页查询单张事件表的向后跳转数据。
     *
     * @param session 回放会话。
     * @param table 表描述。
     * @param targetTime 目标回放时间。
     * @return 单表事件帧。
     */
    private List<ReplayFrame> queryBackwardEventTable(ReplaySession session,
                                                     ReplayTableDescriptor table,
                                                     long targetTime) {
        List<ReplayFrame> frames = new ArrayList<ReplayFrame>();
        int offset = 0;
        while (true) {
            ReplayCursor cursor = new ReplayCursor(table.getTableName(), pageSize, offset);
            List<ReplayFrame> page = frameRepository.findBackwardJumpEventFrames(
                    table,
                    session.getSimulationStartTime(),
                    targetTime,
                    cursor);
            if (page == null || page.isEmpty()) {
                break;
            }
            frames.addAll(page);
            offset += page.size();
            if (page.size() < pageSize) {
                break;
            }
        }
        return frames;
    }

    /**
     * 分页查询单张事件表的向前跳转数据。
     *
     * @param table 表描述。
     * @param currentTime 当前回放时间。
     * @param targetTime 目标回放时间。
     * @return 单表事件帧。
     */
    private List<ReplayFrame> queryForwardEventTable(ReplayTableDescriptor table,
                                                    long currentTime,
                                                    long targetTime) {
        List<ReplayFrame> frames = new ArrayList<ReplayFrame>();
        int offset = 0;
        while (true) {
            ReplayCursor cursor = new ReplayCursor(table.getTableName(), pageSize, offset);
            List<ReplayFrame> page = frameRepository.findForwardJumpEventFrames(
                    table,
                    currentTime,
                    targetTime,
                    cursor);
            if (page == null || page.isEmpty()) {
                break;
            }
            frames.addAll(page);
            offset += page.size();
            if (page.size() < pageSize) {
                break;
            }
        }
        return frames;
    }

    /**
     * 发布周期表目标时间前最后一帧。
     *
     * @param session 回放会话。
     * @param targetTime 目标回放时间。
     */
    private void publishPeriodicSnapshots(ReplaySession session, long targetTime) {
        publishFrames(session, querySafely(() -> queryPeriodicSnapshots(session, targetTime)));
    }

    /**
     * 查询周期表目标时间前最后一帧。
     *
     * @param session 回放会话。
     * @param targetTime 目标回放时间。
     * @return 周期表快照帧。
     */
    private List<ReplayFrame> queryPeriodicSnapshots(ReplaySession session, long targetTime) {
        List<ReplayFrame> snapshots = new ArrayList<ReplayFrame>();
        for (ReplayTableDescriptor table : session.getPeriodicTables()) {
            Optional<ReplayFrame> frame = frameRepository.findPeriodicLastFrame(table, targetTime);
            if (frame.isPresent()) {
                snapshots.add(frame.get());
            }
        }
        return frameMergeService.merge(Collections.singletonList(snapshots));
    }

    /**
     * 发布帧列表。
     *
     * @param session 回放会话。
     * @param frames 帧列表。
     */
    private void publishFrames(ReplaySession session, List<ReplayFrame> frames) {
        for (ReplayFrame frame : frames) {
            // 发布跳转补偿帧或周期快照帧。
            situationPublisher.publish(session.getInstanceId(), frame);
        }
    }

    /**
     * 执行 TDengine 查询并记录查询失败指标。
     *
     * @param query 回放查询动作。
     * @return 查询结果帧。
     */
    private List<ReplayFrame> querySafely(FrameQuery query) {
        try {
            return query.query();
        } catch (RuntimeException exception) {
            replayMetrics.recordQueryFailure();
            throw exception;
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
     * 将请求时间限制在回放范围内。
     *
     * @param session 回放会话。
     * @param requestedTime 请求时间。
     * @return 限制后的目标时间。
     */
    private long clamp(ReplaySession session, long requestedTime) {
        if (requestedTime < session.getSimulationStartTime()) {
            return session.getSimulationStartTime();
        }
        if (requestedTime > session.getSimulationEndTime()) {
            return session.getSimulationEndTime();
        }
        return requestedTime;
    }

    /**
     * 回放帧查询动作。
     */
    private interface FrameQuery {

        /**
         * 执行回放帧查询。
         *
         * @return 回放帧列表。
         */
        List<ReplayFrame> query();
    }
}

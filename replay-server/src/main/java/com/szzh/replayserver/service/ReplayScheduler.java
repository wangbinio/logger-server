package com.szzh.replayserver.service;

import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.mq.ReplaySituationPublisher;
import com.szzh.replayserver.repository.ReplayFrameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 回放连续窗口调度器。
 */
@Service
public class ReplayScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReplayScheduler.class);

    private final ReplayFrameRepository frameRepository;

    private final ReplayFrameMergeService frameMergeService;

    private final ReplaySituationPublisher situationPublisher;

    private final int pageSize;

    private final long tickMillis;

    private final ScheduledExecutorService executorService;

    private final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks =
            new ConcurrentHashMap<String, ScheduledFuture<?>>();

    /**
     * 创建回放连续窗口调度器。
     *
     * @param frameRepository 回放帧查询 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param properties 回放服务配置。
     */
    @Autowired
    public ReplayScheduler(ReplayFrameRepository frameRepository,
                           ReplayFrameMergeService frameMergeService,
                           ReplaySituationPublisher situationPublisher,
                           ReplayServerProperties properties) {
        this(frameRepository,
                frameMergeService,
                situationPublisher,
                properties.getReplay().getQuery().getPageSize(),
                properties.getReplay().getScheduler().getTickMillis());
    }

    /**
     * 创建回放连续窗口调度器。
     *
     * @param frameRepository 回放帧查询 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param pageSize 查询分页大小。
     * @param tickMillis 调度间隔。
     */
    public ReplayScheduler(ReplayFrameRepository frameRepository,
                           ReplayFrameMergeService frameMergeService,
                           ReplaySituationPublisher situationPublisher,
                           int pageSize,
                           long tickMillis) {
        this.frameRepository = Objects.requireNonNull(frameRepository, "frameRepository 不能为空");
        this.frameMergeService = Objects.requireNonNull(frameMergeService, "frameMergeService 不能为空");
        this.situationPublisher = Objects.requireNonNull(situationPublisher, "situationPublisher 不能为空");
        this.pageSize = Math.max(1, pageSize);
        this.tickMillis = Math.max(1L, tickMillis);
        this.executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "replay-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 启动指定会话的周期调度。
     *
     * @param session 回放会话。
     */
    public void schedule(ReplaySession session) {
        ReplaySession replaySession = Objects.requireNonNull(session, "session 不能为空");
        scheduledTasks.computeIfAbsent(replaySession.getInstanceId(), instanceId ->
                executorService.scheduleWithFixedDelay(
                        () -> safeTick(replaySession),
                        0L,
                        tickMillis,
                        TimeUnit.MILLISECONDS));
    }

    /**
     * 停止指定实例的周期调度。
     *
     * @param instanceId 实例 ID。
     */
    public void cancel(String instanceId) {
        ScheduledFuture<?> scheduledFuture = scheduledTasks.remove(requireInstanceId(instanceId));
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    /**
     * 执行一次连续回放窗口调度。
     *
     * @param session 回放会话。
     */
    public void tick(ReplaySession session) {
        ReplaySession replaySession = Objects.requireNonNull(session, "session 不能为空");
        if (replaySession.getState() != ReplaySessionState.RUNNING) {
            return;
        }

        long fromExclusive = replaySession.getLastDispatchedSimTime();
        long toInclusive = replaySession.getReplayClock().currentTime();
        if (toInclusive <= fromExclusive) {
            completeIfReachedEnd(replaySession, toInclusive);
            return;
        }

        try {
            List<List<ReplayFrame>> framePages = queryWindowFrames(replaySession, fromExclusive, toInclusive);
            List<ReplayFrame> frames = frameMergeService.merge(framePages);
            for (ReplayFrame frame : frames) {
                if (replaySession.getState() != ReplaySessionState.RUNNING) {
                    return;
                }
                situationPublisher.publish(replaySession.getInstanceId(), frame);
            }
            replaySession.addDispatchedFrameCount(frames.size());
            replaySession.advanceLastDispatchedSimTime(toInclusive);
            completeIfReachedEnd(replaySession, toInclusive);
        } catch (RuntimeException exception) {
            markFailedIfActive(replaySession, exception);
            throw exception;
        }
    }

    /**
     * 关闭调度线程池。
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * 安全执行周期调度，避免定时任务异常退出。
     *
     * @param session 回放会话。
     */
    private void safeTick(ReplaySession session) {
        try {
            tick(session);
        } catch (RuntimeException exception) {
            log.warn("result=replay_scheduler_tick_failed instanceId={} topic=- messageType=-1 messageCode=-1 senderId=-1 simtime={} sessionState={} reason={}",
                    session.getInstanceId(), session.getReplayClock().currentTime(), session.getState(), exception.getMessage());
        }
    }

    /**
     * 查询当前窗口内的所有回放帧。
     *
     * @param session 回放会话。
     * @param fromExclusive 左开仿真时间。
     * @param toInclusive 右闭仿真时间。
     * @return 多表分页帧。
     */
    private List<List<ReplayFrame>> queryWindowFrames(ReplaySession session,
                                                      long fromExclusive,
                                                      long toInclusive) {
        List<List<ReplayFrame>> framePages = new ArrayList<List<ReplayFrame>>();
        for (ReplayTableDescriptor tableDescriptor : allTables(session)) {
            framePages.add(queryTableWindowFrames(session, tableDescriptor, fromExclusive, toInclusive));
        }
        return framePages;
    }

    /**
     * 分页查询单张表当前窗口内的回放帧。
     *
     * @param session 回放会话。
     * @param tableDescriptor 表描述。
     * @param fromExclusive 左开仿真时间。
     * @param toInclusive 右闭仿真时间。
     * @return 单表回放帧。
     */
    private List<ReplayFrame> queryTableWindowFrames(ReplaySession session,
                                                     ReplayTableDescriptor tableDescriptor,
                                                     long fromExclusive,
                                                     long toInclusive) {
        List<ReplayFrame> frames = new ArrayList<ReplayFrame>();
        int offset = 0;
        while (true) {
            ReplayCursor cursor = new ReplayCursor(tableDescriptor.getTableName(), pageSize, offset);
            List<ReplayFrame> page = frameRepository.findWindowFrames(
                    tableDescriptor,
                    fromExclusive,
                    toInclusive,
                    cursor);
            if (page == null || page.isEmpty()) {
                break;
            }
            frames.addAll(page);
            offset += page.size();
            session.updateCursor(cursor.advance(page.size()));
            if (page.size() < pageSize) {
                break;
            }
        }
        return frames;
    }

    /**
     * 获取会话内所有需要连续回放的表。
     *
     * @param session 回放会话。
     * @return 表描述列表。
     */
    private List<ReplayTableDescriptor> allTables(ReplaySession session) {
        List<ReplayTableDescriptor> tables = new ArrayList<ReplayTableDescriptor>();
        tables.addAll(session.getEventTables());
        tables.addAll(session.getPeriodicTables());
        return tables;
    }

    /**
     * 到达结束时间时标记自然完成。
     *
     * @param session 回放会话。
     * @param toInclusive 当前窗口右边界。
     */
    private void completeIfReachedEnd(ReplaySession session, long toInclusive) {
        if (session.getState() == ReplaySessionState.RUNNING
                && toInclusive >= session.getSimulationEndTime()) {
            session.markCompleted();
        }
    }

    /**
     * 在会话仍处于活动状态时标记失败。
     *
     * @param session 回放会话。
     * @param exception 原始异常。
     */
    private void markFailedIfActive(ReplaySession session, RuntimeException exception) {
        if (!session.getState().isTerminal()) {
            session.markFailed(exception.getMessage());
        }
    }

    /**
     * 校验实例 ID。
     *
     * @param instanceId 实例 ID。
     * @return 原始实例 ID。
     */
    private String requireInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return instanceId.trim();
    }
}

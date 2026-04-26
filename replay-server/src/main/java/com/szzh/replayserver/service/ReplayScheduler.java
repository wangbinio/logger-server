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

    private final ReplayMetrics replayMetrics;

    private final int pageSize;

    private final long tickMillis;

    private final int batchSize;

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
     * @param replayMetrics 回放指标。
     */
    @Autowired
    public ReplayScheduler(ReplayFrameRepository frameRepository,
                           ReplayFrameMergeService frameMergeService,
                           ReplaySituationPublisher situationPublisher,
                           ReplayServerProperties properties,
                           ReplayMetrics replayMetrics) {
        this(frameRepository,
                frameMergeService,
                situationPublisher,
                properties.getReplay().getQuery().getPageSize(),
                properties.getReplay().getScheduler().getTickMillis(),
                properties.getReplay().getPublish().getBatchSize(),
                replayMetrics);
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
        this(frameRepository, frameMergeService, situationPublisher, pageSize, tickMillis, new ReplayMetrics());
    }

    /**
     * 创建回放连续窗口调度器。
     *
     * @param frameRepository 回放帧查询 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param pageSize 查询分页大小。
     * @param tickMillis 调度间隔。
     * @param batchSize 发布批次大小。
     */
    public ReplayScheduler(ReplayFrameRepository frameRepository,
                           ReplayFrameMergeService frameMergeService,
                           ReplaySituationPublisher situationPublisher,
                           int pageSize,
                           long tickMillis,
                           int batchSize) {
        this(frameRepository, frameMergeService, situationPublisher, pageSize, tickMillis, batchSize, new ReplayMetrics());
    }

    /**
     * 创建回放连续窗口调度器。
     *
     * @param frameRepository 回放帧查询 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param pageSize 查询分页大小。
     * @param tickMillis 调度间隔。
     * @param replayMetrics 回放指标。
     */
    public ReplayScheduler(ReplayFrameRepository frameRepository,
                           ReplayFrameMergeService frameMergeService,
                           ReplaySituationPublisher situationPublisher,
                           int pageSize,
                           long tickMillis,
                           ReplayMetrics replayMetrics) {
        this(frameRepository, frameMergeService, situationPublisher, pageSize, tickMillis, 500, replayMetrics);
    }

    /**
     * 创建回放连续窗口调度器。
     *
     * @param frameRepository 回放帧查询 Repository。
     * @param frameMergeService 回放帧归并服务。
     * @param situationPublisher 回放态势发布器。
     * @param pageSize 查询分页大小。
     * @param tickMillis 调度间隔。
     * @param batchSize 发布批次大小。
     * @param replayMetrics 回放指标。
     */
    public ReplayScheduler(ReplayFrameRepository frameRepository,
                           ReplayFrameMergeService frameMergeService,
                           ReplaySituationPublisher situationPublisher,
                           int pageSize,
                           long tickMillis,
                           int batchSize,
                           ReplayMetrics replayMetrics) {
        this.frameRepository = Objects.requireNonNull(frameRepository, "frameRepository 不能为空");
        this.frameMergeService = Objects.requireNonNull(frameMergeService, "frameMergeService 不能为空");
        this.situationPublisher = Objects.requireNonNull(situationPublisher, "situationPublisher 不能为空");
        this.replayMetrics = Objects.requireNonNull(replayMetrics, "replayMetrics 不能为空");
        this.pageSize = Math.max(1, pageSize);
        this.tickMillis = Math.max(1L, tickMillis);
        this.batchSize = Math.max(1, batchSize);
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
        synchronized (replaySession) {
            doTick(replaySession);
        }
    }

    /**
     * 在会话锁内执行一次连续回放窗口调度。
     *
     * @param replaySession 回放会话。
     */
    private void doTick(ReplaySession replaySession) {
        if (replaySession.getState() != ReplaySessionState.RUNNING) {
            return;
        }

        long fromExclusive = replaySession.getLastDispatchedSimTime();
        long toInclusive = replaySession.getReplayClock().currentTime();
        if (toInclusive <= fromExclusive) {
            completeIfReachedEnd(replaySession, toInclusive);
            return;
        }

        List<List<ReplayFrame>> framePages;
        try {
            // 查询当前回放窗口内的 TDengine 帧数据。
            framePages = queryWindowFrames(replaySession, fromExclusive, toInclusive);
        } catch (RuntimeException exception) {
            replayMetrics.recordQueryFailure();
            markFailedIfActive(replaySession, exception);
            throw exception;
        }

        try {
            List<ReplayFrame> frames = frameMergeService.merge(framePages);
            BatchPublishResult publishResult = publishFramesInBatches(replaySession, frames);
            if (publishResult.getPublishedCount() > 0L) {
                replaySession.addDispatchedFrameCount(publishResult.getPublishedCount());
            }
            if (!publishResult.isCompleted()) {
                if (publishResult.getLastPublishedSimTime() > replaySession.getLastDispatchedSimTime()) {
                    replaySession.advanceLastDispatchedSimTime(publishResult.getLastPublishedSimTime());
                }
                return;
            }
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
            log.warn("result=replay_scheduler_tick_failed instanceId={} topic=- messageType=-1 messageCode=-1 senderId=-1 currentReplayTime={} lastDispatchedSimTime={} rate={} replayState={} reason={}",
                    session.getInstanceId(), session.getReplayClock().currentTime(), session.getLastDispatchedSimTime(), session.getRate(), session.getState(), exception.getMessage());
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
     * 按配置批次发布连续回放帧，并在批次之间检查会话状态。
     *
     * @param session 回放会话。
     * @param frames 已归并排序的回放帧。
     * @return 批量发布结果。
     */
    private BatchPublishResult publishFramesInBatches(ReplaySession session, List<ReplayFrame> frames) {
        int index = 0;
        long publishedCount = 0L;
        long lastPublishedSimTime = session.getLastDispatchedSimTime();
        while (index < frames.size()) {
            if (session.getState() != ReplaySessionState.RUNNING) {
                return BatchPublishResult.interrupted(publishedCount, lastPublishedSimTime);
            }
            int endExclusive = Math.min(index + batchSize, frames.size());
            for (int frameIndex = index; frameIndex < endExclusive; frameIndex++) {
                ReplayFrame frame = frames.get(frameIndex);
                // 同一批次内保持原始归并顺序发布，批次结束后再检查状态。
                situationPublisher.publish(session.getInstanceId(), frame);
                publishedCount++;
                lastPublishedSimTime = Math.max(lastPublishedSimTime, frame.getSimTime());
            }
            if (session.getState() != ReplaySessionState.RUNNING) {
                return BatchPublishResult.interrupted(publishedCount, lastPublishedSimTime);
            }
            index = endExclusive;
        }
        return BatchPublishResult.completed(publishedCount, lastPublishedSimTime);
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

    /**
     * 连续回放批量发布结果。
     */
    private static class BatchPublishResult {

        private final boolean completed;

        private final long publishedCount;

        private final long lastPublishedSimTime;

        /**
         * 创建批量发布结果。
         *
         * @param completed 是否完整发布。
         * @param publishedCount 已发布数量。
         * @param lastPublishedSimTime 最后发布时间。
         */
        private BatchPublishResult(boolean completed, long publishedCount, long lastPublishedSimTime) {
            this.completed = completed;
            this.publishedCount = publishedCount;
            this.lastPublishedSimTime = lastPublishedSimTime;
        }

        /**
         * 创建完整发布结果。
         *
         * @param publishedCount 已发布数量。
         * @param lastPublishedSimTime 最后发布时间。
         * @return 发布结果。
         */
        private static BatchPublishResult completed(long publishedCount, long lastPublishedSimTime) {
            return new BatchPublishResult(true, publishedCount, lastPublishedSimTime);
        }

        /**
         * 创建中断发布结果。
         *
         * @param publishedCount 已发布数量。
         * @param lastPublishedSimTime 最后发布时间。
         * @return 发布结果。
         */
        private static BatchPublishResult interrupted(long publishedCount, long lastPublishedSimTime) {
            return new BatchPublishResult(false, publishedCount, lastPublishedSimTime);
        }

        /**
         * 判断是否完整发布。
         *
         * @return 是否完整发布。
         */
        private boolean isCompleted() {
            return completed;
        }

        /**
         * 获取已发布数量。
         *
         * @return 已发布数量。
         */
        private long getPublishedCount() {
            return publishedCount;
        }

        /**
         * 获取最后发布时间。
         *
         * @return 最后发布时间。
         */
        private long getLastPublishedSimTime() {
            return lastPublishedSimTime;
        }
    }
}

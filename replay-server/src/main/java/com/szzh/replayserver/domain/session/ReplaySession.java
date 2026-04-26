package com.szzh.replayserver.domain.session;

import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放会话聚合根。
 */
@Getter
public class ReplaySession {

    private final String instanceId;

    private final ReplayTimeRange timeRange;

    private final long simulationStartTime;

    private final long simulationEndTime;

    private final long duration;

    private final ReplayClock replayClock;

    private final List<ReplayTableDescriptor> eventTables;

    private final List<ReplayTableDescriptor> periodicTables;

    @Getter(AccessLevel.NONE)
    private final ConcurrentMap<String, ReplayCursor> cursors = new ConcurrentHashMap<String, ReplayCursor>();

    @Getter(AccessLevel.NONE)
    private final AtomicLong dispatchedFrameCount = new AtomicLong();

    private final long createdAtMillis;

    private volatile ReplaySessionState state = ReplaySessionState.PREPARING;

    private volatile double rate = 1.0D;

    private volatile long lastDispatchedSimTime;

    private volatile String lastErrorMessage;

    private volatile long lastErrorTimeMillis;

    private volatile Object broadcastConsumerHandle;

    /**
     * 创建回放会话。
     *
     * @param instanceId 实例 ID。
     * @param timeRange 回放时间范围。
     * @param eventTables 事件表描述列表。
     * @param periodicTables 周期表描述列表。
     * @param replayClock 回放时钟。
     */
    public ReplaySession(String instanceId,
                         ReplayTimeRange timeRange,
                         List<ReplayTableDescriptor> eventTables,
                         List<ReplayTableDescriptor> periodicTables,
                         ReplayClock replayClock) {
        this.instanceId = requireInstanceId(instanceId);
        this.timeRange = Objects.requireNonNull(timeRange, "timeRange 不能为空");
        this.replayClock = Objects.requireNonNull(replayClock, "replayClock 不能为空");
        this.eventTables = immutableCopy(eventTables);
        this.periodicTables = immutableCopy(periodicTables);
        this.simulationStartTime = timeRange.getStartTime();
        this.simulationEndTime = timeRange.getEndTime();
        this.duration = timeRange.getDuration();
        this.lastDispatchedSimTime = simulationStartTime;
        this.createdAtMillis = System.currentTimeMillis();
    }

    /**
     * 标记会话准备完成。
     */
    public synchronized void markReady() {
        if (state == ReplaySessionState.READY) {
            return;
        }
        ensureNotTerminal();
        if (state != ReplaySessionState.PREPARING) {
            throw new IllegalStateException("只有 PREPARING 状态可以迁移到 READY");
        }
        this.state = ReplaySessionState.READY;
    }

    /**
     * 启动回放会话。
     */
    public synchronized void start() {
        if (state == ReplaySessionState.RUNNING) {
            return;
        }
        ensureNotTerminal();
        if (state != ReplaySessionState.READY) {
            throw new IllegalStateException("只有 READY 状态可以启动回放");
        }
        replayClock.start();
        this.rate = replayClock.getRate();
        this.state = ReplaySessionState.RUNNING;
    }

    /**
     * 暂停回放会话。
     */
    public synchronized void pause() {
        if (state == ReplaySessionState.PAUSED) {
            return;
        }
        ensureNotTerminal();
        if (state != ReplaySessionState.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 状态可以暂停回放");
        }
        replayClock.pause();
        this.state = ReplaySessionState.PAUSED;
    }

    /**
     * 继续回放会话。
     */
    public synchronized void resume() {
        if (state == ReplaySessionState.RUNNING) {
            return;
        }
        ensureNotTerminal();
        if (state != ReplaySessionState.PAUSED) {
            throw new IllegalStateException("只有 PAUSED 状态可以继续回放");
        }
        replayClock.resume();
        this.state = ReplaySessionState.RUNNING;
    }

    /**
     * 更新回放倍率。
     *
     * @param newRate 新倍率。
     */
    public synchronized void updateRate(double newRate) {
        ensureNotTerminal();
        if (state != ReplaySessionState.RUNNING && state != ReplaySessionState.PAUSED) {
            throw new IllegalStateException("只有 RUNNING 或 PAUSED 状态可以更新倍率");
        }
        replayClock.updateRate(newRate);
        this.rate = newRate;
    }

    /**
     * 跳转到目标回放时间。
     *
     * @param targetTime 目标回放时间。
     * @return 边界限制后的实际时间。
     */
    public synchronized long jumpTo(long targetTime) {
        ensureNotTerminal();
        if (state != ReplaySessionState.READY
                && state != ReplaySessionState.RUNNING
                && state != ReplaySessionState.PAUSED) {
            throw new IllegalStateException("当前状态不允许时间跳转");
        }
        return replayClock.jumpTo(targetTime);
    }

    /**
     * 标记会话自然完成。
     */
    public synchronized void markCompleted() {
        if (state == ReplaySessionState.COMPLETED) {
            return;
        }
        ensureNotTerminal();
        replayClock.jumpTo(simulationEndTime);
        if (replayClock.isRunning()) {
            replayClock.pause();
        }
        this.state = ReplaySessionState.COMPLETED;
    }

    /**
     * 标记会话失败。
     *
     * @param errorMessage 失败原因。
     */
    public synchronized void markFailed(String errorMessage) {
        if (state == ReplaySessionState.FAILED) {
            return;
        }
        ensureNotTerminal();
        this.lastErrorMessage = errorMessage;
        this.lastErrorTimeMillis = System.currentTimeMillis();
        this.state = ReplaySessionState.FAILED;
    }

    /**
     * 停止回放会话。
     */
    public synchronized void stop() {
        if (state == ReplaySessionState.STOPPED) {
            return;
        }
        if (state.isTerminal()) {
            return;
        }
        if (replayClock.isRunning()) {
            replayClock.pause();
        }
        this.state = ReplaySessionState.STOPPED;
    }

    /**
     * 推进已成功发布的仿真时间水位。
     *
     * @param simTime 已成功发布的仿真时间。
     */
    public synchronized void advanceLastDispatchedSimTime(long simTime) {
        if (simTime < lastDispatchedSimTime) {
            throw new IllegalArgumentException("回放水位不能回退");
        }
        if (simTime > simulationEndTime) {
            throw new IllegalArgumentException("回放水位不能超过结束时间");
        }
        this.lastDispatchedSimTime = simTime;
    }

    /**
     * 同步已成功发布的仿真时间水位。
     *
     * @param simTime 已成功发布的仿真时间。
     */
    public synchronized void syncLastDispatchedSimTime(long simTime) {
        if (simTime < simulationStartTime) {
            throw new IllegalArgumentException("回放水位不能小于开始时间");
        }
        if (simTime > simulationEndTime) {
            throw new IllegalArgumentException("回放水位不能超过结束时间");
        }
        this.lastDispatchedSimTime = simTime;
    }

    /**
     * 增加已发布帧数量。
     *
     * @param count 已发布帧数量。
     */
    public void addDispatchedFrameCount(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("已发布帧数量不能小于 0");
        }
        this.dispatchedFrameCount.addAndGet(count);
    }

    /**
     * 获取已发布帧数量。
     *
     * @return 已发布帧数量。
     */
    public long dispatchedFrameCount() {
        return dispatchedFrameCount.get();
    }

    /**
     * 设置实例级广播消费者句柄。
     *
     * @param broadcastConsumerHandle 广播消费者句柄。
     */
    public void setBroadcastConsumerHandle(Object broadcastConsumerHandle) {
        this.broadcastConsumerHandle = broadcastConsumerHandle;
    }

    /**
     * 更新指定子表的分页游标。
     *
     * @param cursor 分页游标。
     */
    public void updateCursor(ReplayCursor cursor) {
        ReplayCursor replayCursor = Objects.requireNonNull(cursor, "cursor 不能为空");
        this.cursors.put(replayCursor.getTableName(), replayCursor);
    }

    /**
     * 获取分页游标快照。
     *
     * @return 分页游标快照。
     */
    public Map<String, ReplayCursor> getCursors() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<String, ReplayCursor>(cursors));
    }

    /**
     * 确保会话尚未进入终态。
     */
    private void ensureNotTerminal() {
        if (state.isTerminal()) {
            throw new IllegalStateException("终态回放会话不允许迁移到其他状态");
        }
    }

    /**
     * 校验实例 ID。
     *
     * @param instanceId 实例 ID。
     * @return 标准化后的实例 ID。
     */
    private String requireInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return instanceId.trim();
    }

    /**
     * 创建不可变表描述副本。
     *
     * @param tables 表描述列表。
     * @return 不可变表描述列表。
     */
    private List<ReplayTableDescriptor> immutableCopy(List<ReplayTableDescriptor> tables) {
        if (tables == null || tables.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReplayTableDescriptor> copy = new ArrayList<ReplayTableDescriptor>();
        for (ReplayTableDescriptor table : tables) {
            copy.add(Objects.requireNonNull(table, "table 不能为空"));
        }
        return Collections.unmodifiableList(copy);
    }
}

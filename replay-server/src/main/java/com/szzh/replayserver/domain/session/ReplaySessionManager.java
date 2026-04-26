package com.szzh.replayserver.domain.session;

import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTimeRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * 回放会话管理器。
 */
public class ReplaySessionManager {

    private final ConcurrentMap<String, ReplaySession> sessions = new ConcurrentHashMap<String, ReplaySession>();

    private final LongSupplier wallClockSupplier;

    /**
     * 使用系统时间创建回放会话管理器。
     */
    public ReplaySessionManager() {
        this(System::currentTimeMillis);
    }

    /**
     * 使用指定时间源创建回放会话管理器。
     *
     * @param wallClockSupplier 墙钟时间提供者。
     */
    public ReplaySessionManager(LongSupplier wallClockSupplier) {
        this.wallClockSupplier = Objects.requireNonNull(wallClockSupplier, "wallClockSupplier 不能为空");
    }

    /**
     * 创建回放会话。
     *
     * @param instanceId 实例 ID。
     * @param timeRange 回放时间范围。
     * @param eventTables 事件表列表。
     * @param periodicTables 周期表列表。
     * @return 新建或已存在的回放会话。
     */
    public ReplaySession createSession(String instanceId,
                                       ReplayTimeRange timeRange,
                                       List<ReplayTableDescriptor> eventTables,
                                       List<ReplayTableDescriptor> periodicTables) {
        String normalizedInstanceId = normalizeInstanceId(instanceId);
        ReplayTimeRange replayTimeRange = Objects.requireNonNull(timeRange, "timeRange 不能为空");
        return sessions.compute(normalizedInstanceId, (key, existingSession) -> {
            if (existingSession == null || existingSession.getState().isTerminal()) {
                ReplayClock replayClock = new ReplayClock(
                        replayTimeRange.getStartTime(),
                        replayTimeRange.getEndTime(),
                        wallClockSupplier);
                return new ReplaySession(key, replayTimeRange, eventTables, periodicTables, replayClock);
            }
            return existingSession;
        });
    }

    /**
     * 放入已构造的回放会话。
     *
     * @param session 回放会话。
     * @return 新建或已存在的回放会话。
     */
    public ReplaySession createSession(ReplaySession session) {
        ReplaySession newSession = Objects.requireNonNull(session, "session 不能为空");
        String normalizedInstanceId = normalizeInstanceId(newSession.getInstanceId());
        return sessions.compute(normalizedInstanceId, (key, existingSession) -> {
            if (existingSession == null || existingSession.getState().isTerminal()) {
                return newSession;
            }
            return existingSession;
        });
    }

    /**
     * 查询回放会话。
     *
     * @param instanceId 实例 ID。
     * @return 回放会话查询结果。
     */
    public Optional<ReplaySession> getSession(String instanceId) {
        return Optional.ofNullable(sessions.get(normalizeInstanceId(instanceId)));
    }

    /**
     * 查询回放会话，不存在时抛出异常。
     *
     * @param instanceId 实例 ID。
     * @return 回放会话。
     */
    public ReplaySession requireSession(String instanceId) {
        return getSession(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("回放会话不存在: " + instanceId));
    }

    /**
     * 停止回放会话。
     *
     * @param instanceId 实例 ID。
     * @return 是否发生实际停止。
     */
    public boolean stopSession(String instanceId) {
        AtomicBoolean changed = new AtomicBoolean(false);
        sessions.computeIfPresent(normalizeInstanceId(instanceId), (key, session) -> {
            synchronized (session) {
                if (session.getState().isTerminal()) {
                    return session;
                }
                session.stop();
                changed.set(true);
                return session;
            }
        });
        return changed.get();
    }

    /**
     * 移除已停止的回放会话。
     *
     * @param instanceId 实例 ID。
     * @return 被移除的回放会话。
     */
    public Optional<ReplaySession> removeSession(String instanceId) {
        AtomicReference<ReplaySession> removedSession = new AtomicReference<ReplaySession>();
        sessions.computeIfPresent(normalizeInstanceId(instanceId), (key, session) -> {
            if (session.getState() != ReplaySessionState.STOPPED) {
                return session;
            }
            removedSession.set(session);
            return null;
        });
        return Optional.ofNullable(removedSession.get());
    }

    /**
     * 获取所有回放会话快照。
     *
     * @return 回放会话快照集合。
     */
    public Collection<ReplaySession> getAllSessions() {
        return Collections.unmodifiableList(new ArrayList<ReplaySession>(sessions.values()));
    }

    /**
     * 获取当前回放会话数量。
     *
     * @return 当前回放会话数量。
     */
    public int size() {
        return sessions.size();
    }

    /**
     * 标准化实例 ID。
     *
     * @param instanceId 实例 ID。
     * @return 标准化后的实例 ID。
     */
    private String normalizeInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return instanceId.trim();
    }
}

package com.szzh.loggerserver.domain.session;

import com.szzh.loggerserver.domain.clock.SimulationClock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 仿真实例会话管理器。
 */
public class SimulationSessionManager {

    private final ConcurrentMap<String, SimulationSession> sessions = new ConcurrentHashMap<>();

    private final Supplier<SimulationClock> clockSupplier;

    /**
     * 使用默认时钟创建会话管理器。
     */
    public SimulationSessionManager() {
        this(SimulationClock::new);
    }

    /**
     * 使用指定时钟工厂创建会话管理器。
     *
     * @param clockSupplier 时钟工厂。
     */
    public SimulationSessionManager(Supplier<SimulationClock> clockSupplier) {
        this.clockSupplier = clockSupplier;
    }

    /**
     * 创建实例会话。
     *
     * @param instanceId 仿真实例 ID。
     * @return 新建或已存在的会话。
     */
    public SimulationSession createSession(String instanceId) {
        String normalizedInstanceId = normalizeInstanceId(instanceId);
        return sessions.compute(normalizedInstanceId, (key, existingSession) -> {
            if (existingSession == null || existingSession.getState() == SimulationSessionState.STOPPED) {
                return new SimulationSession(key, clockSupplier.get());
            }
            return existingSession;
        });
    }

    /**
     * 查询实例会话。
     *
     * @param instanceId 仿真实例 ID。
     * @return 会话查询结果。
     */
    public Optional<SimulationSession> getSession(String instanceId) {
        return Optional.ofNullable(sessions.get(normalizeInstanceId(instanceId)));
    }

    /**
     * 查询实例会话，不存在则抛出异常。
     *
     * @param instanceId 仿真实例 ID。
     * @return 实例会话。
     */
    public SimulationSession requireSession(String instanceId) {
        return getSession(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("实例会话不存在: " + instanceId));
    }

    /**
     * 停止实例会话。
     *
     * @param instanceId 仿真实例 ID。
     * @return 是否发生了实际停止。
     */
    public boolean stopSession(String instanceId) {
        AtomicBoolean changed = new AtomicBoolean(false);
        sessions.computeIfPresent(normalizeInstanceId(instanceId), (key, session) -> {
            synchronized (session) {
                if (session.getState() == SimulationSessionState.STOPPED) {
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
     * 移除已停止会话。
     *
     * @param instanceId 仿真实例 ID。
     * @return 被移除的会话。
     */
    public Optional<SimulationSession> removeSession(String instanceId) {
        AtomicReference<SimulationSession> removedSession = new AtomicReference<>();
        sessions.computeIfPresent(normalizeInstanceId(instanceId), (key, session) -> {
            if (session.getState() != SimulationSessionState.STOPPED) {
                return session;
            }
            removedSession.set(session);
            return null;
        });
        return Optional.ofNullable(removedSession.get());
    }

    /**
     * 获取所有会话快照。
     *
     * @return 会话快照集合。
     */
    public Collection<SimulationSession> getAllSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
    }

    /**
     * 获取当前会话数量。
     *
     * @return 当前会话数量。
     */
    public int size() {
        return sessions.size();
    }

    /**
     * 标准化实例 ID。
     *
     * @param instanceId 仿真实例 ID。
     * @return 标准化后的实例 ID。
     */
    private String normalizeInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return instanceId.trim();
    }
}

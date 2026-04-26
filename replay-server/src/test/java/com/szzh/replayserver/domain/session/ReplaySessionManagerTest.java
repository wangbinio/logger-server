package com.szzh.replayserver.domain.session;

import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放会话管理器测试。
 */
class ReplaySessionManagerTest {

    /**
     * 验证创建和重复创建同一实例时返回同一个非终态会话。
     */
    @Test
    void shouldCreateSessionIdempotently() {
        ReplaySessionManager sessionManager = new ReplaySessionManager(new AtomicLong(1_000L)::get);
        ReplayTimeRange timeRange = new ReplayTimeRange(1_000L, 2_000L);

        ReplaySession first = sessionManager.createSession(
                "instance-001", timeRange, eventTables(), periodicTables());
        ReplaySession second = sessionManager.createSession(
                "instance-001", timeRange, eventTables(), periodicTables());

        Assertions.assertSame(first, second);
        Assertions.assertEquals(ReplaySessionState.PREPARING, first.getState());
        Assertions.assertEquals(1, sessionManager.size());
    }

    /**
     * 验证查询、停止和重复停止保持安全幂等。
     */
    @Test
    void shouldQueryAndStopSessionIdempotently() {
        ReplaySessionManager sessionManager = new ReplaySessionManager(new AtomicLong(1_000L)::get);
        ReplaySession session = sessionManager.createSession(
                "instance-001", new ReplayTimeRange(1_000L, 2_000L), eventTables(), periodicTables());

        Optional<ReplaySession> found = sessionManager.getSession("instance-001");

        Assertions.assertTrue(found.isPresent());
        Assertions.assertSame(session, sessionManager.requireSession("instance-001"));
        Assertions.assertTrue(sessionManager.stopSession("instance-001"));
        Assertions.assertFalse(sessionManager.stopSession("instance-001"));
        Assertions.assertFalse(sessionManager.stopSession("missing"));
        Assertions.assertEquals(ReplaySessionState.STOPPED, session.getState());
    }

    /**
     * 验证只移除已停止会话。
     */
    @Test
    void shouldRemoveStoppedSessionSafely() {
        ReplaySessionManager sessionManager = new ReplaySessionManager(new AtomicLong(1_000L)::get);
        sessionManager.createSession(
                "instance-001", new ReplayTimeRange(1_000L, 2_000L), eventTables(), periodicTables());

        Assertions.assertFalse(sessionManager.removeSession("instance-001").isPresent());

        sessionManager.stopSession("instance-001");

        Assertions.assertTrue(sessionManager.removeSession("instance-001").isPresent());
        Assertions.assertFalse(sessionManager.getSession("instance-001").isPresent());
        Assertions.assertEquals(0, sessionManager.size());
    }

    /**
     * 验证自然完成会话收到停止后仍会被移除。
     */
    @Test
    void shouldStopAndRemoveCompletedSession() {
        ReplaySessionManager sessionManager = new ReplaySessionManager(new AtomicLong(1_000L)::get);
        ReplaySession session = sessionManager.createSession(
                "instance-001", new ReplayTimeRange(1_000L, 2_000L), eventTables(), periodicTables());
        session.markReady();
        session.start();
        session.markCompleted();

        Assertions.assertTrue(sessionManager.stopSession("instance-001"));
        Assertions.assertEquals(ReplaySessionState.STOPPED, session.getState());
        Assertions.assertTrue(sessionManager.removeSession("instance-001").isPresent());
        Assertions.assertFalse(sessionManager.getSession("instance-001").isPresent());
    }

    /**
     * 验证失败会话收到停止后仍会被移除。
     */
    @Test
    void shouldStopAndRemoveFailedSession() {
        ReplaySessionManager sessionManager = new ReplaySessionManager(new AtomicLong(1_000L)::get);
        ReplaySession session = sessionManager.createSession(
                "instance-001", new ReplayTimeRange(1_000L, 2_000L), eventTables(), periodicTables());
        session.markFailed("boom");

        Assertions.assertTrue(sessionManager.stopSession("instance-001"));
        Assertions.assertEquals(ReplaySessionState.STOPPED, session.getState());
        Assertions.assertTrue(sessionManager.removeSession("instance-001").isPresent());
        Assertions.assertFalse(sessionManager.getSession("instance-001").isPresent());
    }

    /**
     * 创建事件表测试数据。
     *
     * @return 事件表列表。
     */
    private java.util.List<ReplayTableDescriptor> eventTables() {
        return Collections.singletonList(new ReplayTableDescriptor(
                "situation_1001_1_7_instance_001", 7, 1001, 1, ReplayTableType.EVENT));
    }

    /**
     * 创建周期表测试数据。
     *
     * @return 周期表列表。
     */
    private java.util.List<ReplayTableDescriptor> periodicTables() {
        return Collections.singletonList(new ReplayTableDescriptor(
                "situation_2001_9_8_instance_001", 8, 2001, 9, ReplayTableType.PERIODIC));
    }
}

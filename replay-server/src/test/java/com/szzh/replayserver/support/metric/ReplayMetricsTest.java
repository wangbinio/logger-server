package com.szzh.replayserver.support.metric;

import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放指标测试。
 */
class ReplayMetricsTest {

    /**
     * 验证活跃会话数只统计非终态会话。
     */
    @Test
    void shouldCountOnlyNonTerminalReplaySessions() {
        ReplaySessionManager sessionManager = new ReplaySessionManager(new AtomicLong(1_000L)::get);
        ReplayMetrics metrics = new ReplayMetrics(sessionManager);
        ReplaySession activeSession = session("active-instance");
        ReplaySession completedSession = session("completed-instance");
        completedSession.markCompleted();
        sessionManager.createSession(activeSession);
        sessionManager.createSession(completedSession);

        Assertions.assertEquals(1L, metrics.activeSessionCount());
    }

    /**
     * 验证各类回放计数器按调用累加。
     */
    @Test
    void shouldAccumulateReplayCounters() {
        ReplayMetrics metrics = new ReplayMetrics();

        metrics.recordPublishSuccess();
        metrics.recordPublishFailure();
        metrics.recordQueryFailure();
        metrics.recordJump();
        metrics.recordStateConflict();
        metrics.recordStateConflict();

        Assertions.assertEquals(1L, metrics.publishedSuccessCount());
        Assertions.assertEquals(1L, metrics.publishedFailureCount());
        Assertions.assertEquals(1L, metrics.queryFailureCount());
        Assertions.assertEquals(1L, metrics.jumpCount());
        Assertions.assertEquals(2L, metrics.stateConflictCount());
    }

    /**
     * 创建测试会话。
     *
     * @param instanceId 实例 ID。
     * @return 回放会话。
     */
    private ReplaySession session(String instanceId) {
        ReplaySession session = new ReplaySession(
                instanceId,
                new ReplayTimeRange(1_000L, 2_000L),
                Collections.singletonList(new ReplayTableDescriptor(
                        "event_table", 7, 1001, 1, ReplayTableType.EVENT)),
                Collections.emptyList(),
                new ReplayClock(1_000L, 2_000L, () -> 1_000L));
        session.markReady();
        return session;
    }
}

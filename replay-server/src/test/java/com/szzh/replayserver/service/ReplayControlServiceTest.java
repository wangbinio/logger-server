package com.szzh.replayserver.service;

import com.szzh.common.protocol.ProtocolData;
import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放控制服务测试。
 */
class ReplayControlServiceTest {

    /**
     * 验证 READY 会话启动后进入 RUNNING 并启动调度器。
     */
    @Test
    void shouldStartReadyReplaySession() {
        Fixture fixture = new Fixture();
        ReplaySession session = fixture.readySession();

        fixture.service.handleStart("instance-001", protocolData("{}"));

        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Mockito.verify(fixture.scheduler).schedule(session);
    }

    /**
     * 验证暂停和继续会驱动时钟状态并控制调度器。
     */
    @Test
    void shouldPauseAndResumeRunningReplaySession() {
        Fixture fixture = new Fixture();
        ReplaySession session = fixture.runningSession();
        Mockito.clearInvocations(fixture.scheduler);

        fixture.service.handlePause("instance-001", protocolData("{}"));
        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());
        Mockito.verify(fixture.scheduler).cancel("instance-001");

        fixture.service.handleResume("instance-001", protocolData("{}"));
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Mockito.verify(fixture.scheduler).schedule(session);
    }

    /**
     * 验证运行或暂停状态下可以更新倍率。
     */
    @Test
    void shouldUpdateReplayRateWhenRunningOrPaused() {
        Fixture fixture = new Fixture();
        ReplaySession session = fixture.runningSession();

        fixture.service.handleRate("instance-001", protocolData("{\"rate\":2.0}"));
        Assertions.assertEquals(2.0D, session.getRate(), 0.0001D);
        Assertions.assertEquals(2.0D, session.getReplayClock().getRate(), 0.0001D);

        fixture.service.handlePause("instance-001", protocolData("{}"));
        fixture.service.handleRate("instance-001", protocolData("{\"rate\":0.5}"));
        Assertions.assertEquals(0.5D, session.getRate(), 0.0001D);
        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());
    }

    /**
     * 验证缺失会话和非法倍率会被安全忽略。
     */
    @Test
    void shouldIgnoreMissingSessionAndInvalidRate() {
        Fixture fixture = new Fixture();
        ReplaySession session = fixture.readySession();

        Assertions.assertDoesNotThrow(() -> fixture.service.handleStart("missing", protocolData("{}")));
        Assertions.assertDoesNotThrow(() -> fixture.service.handleRate("instance-001", protocolData("{\"rate\":0}")));

        Assertions.assertEquals(ReplaySessionState.READY, session.getState());
        Assertions.assertEquals(1.0D, session.getRate(), 0.0001D);
    }

    /**
     * 验证运行状态下跳转会暂停调度、委托跳转服务并恢复调度。
     */
    @Test
    void shouldDelegateJumpAndResumeSchedulerWhenRunning() {
        Fixture fixture = new Fixture();
        ReplaySession session = fixture.runningSession();
        Mockito.clearInvocations(fixture.scheduler);

        fixture.service.handleJump("instance-001", protocolData("{\"time\":1500}"));

        Mockito.verify(fixture.scheduler).cancel("instance-001");
        Mockito.verify(fixture.jumpService).jump(session, 1_500L);
        Mockito.verify(fixture.scheduler).schedule(session);
    }

    /**
     * 验证自然完成态仍接受时间跳转控制命令。
     */
    @Test
    void shouldDelegateJumpWhenSessionCompleted() {
        Fixture fixture = new Fixture();
        ReplaySession session = fixture.runningSession();
        session.markCompleted();
        Mockito.clearInvocations(fixture.scheduler);

        fixture.service.handleJump("instance-001", protocolData("{\"time\":1500}"));

        Mockito.verify(fixture.jumpService).jump(session, 1_500L);
        Mockito.verifyNoInteractions(fixture.scheduler);
        Assertions.assertEquals(0L, fixture.metrics.stateConflictCount());
    }

    /**
     * 验证非法状态控制会记录状态冲突指标。
     */
    @Test
    void shouldRecordStateConflictWhenControlRejected() {
        Fixture fixture = new Fixture();
        fixture.readySession();

        fixture.service.handlePause("instance-001", protocolData("{}"));
        fixture.service.handleResume("missing", protocolData("{}"));

        Assertions.assertEquals(2L, fixture.metrics.stateConflictCount());
        Mockito.verifyNoInteractions(fixture.scheduler);
    }

    /**
     * 构建协议数据。
     *
     * @param json JSON 载荷。
     * @return 协议数据。
     */
    private static ProtocolData protocolData(String json) {
        ProtocolData protocolData = new ProtocolData();
        protocolData.setRawData(json.getBytes(StandardCharsets.UTF_8));
        return protocolData;
    }

    /**
     * 控制服务测试夹具。
     */
    private static final class Fixture {

        private final AtomicLong wallClock = new AtomicLong(1_000L);

        private final ReplaySessionManager sessionManager = new ReplaySessionManager(wallClock::get);

        private final ReplayScheduler scheduler = Mockito.mock(ReplayScheduler.class);

        private final ReplayJumpService jumpService = Mockito.mock(ReplayJumpService.class);

        private final ReplayMetrics metrics = new ReplayMetrics();

        private final ReplayControlService service =
                new ReplayControlService(sessionManager, scheduler, jumpService, metrics);

        /**
         * 创建 READY 会话。
         *
         * @return READY 会话。
         */
        private ReplaySession readySession() {
            ReplaySession session = new ReplaySession(
                    "instance-001",
                    new ReplayTimeRange(1_000L, 2_000L),
                    Collections.singletonList(new ReplayTableDescriptor(
                            "event_table", 7, 1001, 1, ReplayTableType.EVENT)),
                    Collections.emptyList(),
                    new ReplayClock(1_000L, 2_000L, wallClock::get));
            session.markReady();
            sessionManager.createSession(session);
            return session;
        }

        /**
         * 创建 RUNNING 会话。
         *
         * @return RUNNING 会话。
         */
        private ReplaySession runningSession() {
            ReplaySession session = readySession();
            service.handleStart("instance-001", protocolData("{}"));
            return session;
        }
    }
}

package com.szzh.loggerserver.domain.session;

import com.szzh.loggerserver.domain.clock.SimulationClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真实例会话管理器测试。
 */
class SimulationSessionManagerTest {

    /**
     * 验证重复创建同一个实例时返回同一个会话。
     */
    @Test
    void shouldCreateSessionIdempotently() {
        SimulationSessionManager sessionManager = new SimulationSessionManager(this::newClock);

        SimulationSession first = sessionManager.createSession("instance-001");
        SimulationSession second = sessionManager.createSession("instance-001");

        Assertions.assertSame(first, second);
        Assertions.assertEquals(SimulationSessionState.PREPARING, first.getState());
        Assertions.assertEquals(1, sessionManager.size());
    }

    /**
     * 验证停止不存在或已停止的会话时保持幂等。
     */
    @Test
    void shouldStopSessionIdempotently() {
        SimulationSessionManager sessionManager = new SimulationSessionManager(this::newClock);

        Assertions.assertFalse(sessionManager.stopSession("missing"));

        SimulationSession session = sessionManager.createSession("instance-001");
        Assertions.assertTrue(sessionManager.stopSession("instance-001"));
        Assertions.assertEquals(SimulationSessionState.STOPPED, session.getState());
        Assertions.assertFalse(sessionManager.stopSession("instance-001"));
    }

    /**
     * 验证会话状态变迁与移除逻辑。
     */
    @Test
    void shouldUpdateStateAndRemoveStoppedSession() {
        SimulationSessionManager sessionManager = new SimulationSessionManager(this::newClock);

        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        session.updateState(SimulationSessionState.RUNNING);

        Assertions.assertEquals(SimulationSessionState.RUNNING, sessionManager.requireSession("instance-001").getState());

        sessionManager.stopSession("instance-001");
        Assertions.assertTrue(sessionManager.removeSession("instance-001").isPresent());
        Assertions.assertFalse(sessionManager.getSession("instance-001").isPresent());
    }

    /**
     * 验证并发创建时只会保留一个会话实例。
     */
    @Test
    void shouldBeConcurrentSafeWhenCreatingSession() throws Exception {
        SimulationSessionManager sessionManager = new SimulationSessionManager(this::newClock);
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        List<Callable<SimulationSession>> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            tasks.add(() -> sessionManager.createSession("instance-001"));
        }

        List<Future<SimulationSession>> futures = executorService.invokeAll(tasks);
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        SimulationSession expected = futures.get(0).get();
        for (Future<SimulationSession> future : futures) {
            Assertions.assertSame(expected, future.get());
        }
        Assertions.assertEquals(1, sessionManager.size());
    }

    /**
     * 创建测试用时钟。
     *
     * @return 时钟实例。
     */
    private SimulationClock newClock() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        return new SimulationClock(wallClock::get);
    }
}

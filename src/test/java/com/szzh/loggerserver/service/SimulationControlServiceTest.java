package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.clock.SimulationClock;
import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.util.ProtocolData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真控制服务测试。
 */
class SimulationControlServiceTest {

    /**
     * 验证启动命令会启动时钟并把会话切到运行态。
     */
    @Test
    void shouldStartSessionWhenReady() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        SimulationControlService controlService = new SimulationControlService(sessionManager);

        controlService.handleStart("instance-001", new ProtocolData());

        Assertions.assertEquals(SimulationSessionState.RUNNING, session.getState());
        Assertions.assertTrue(session.getSimulationClock().isInitialized());
        Assertions.assertTrue(session.getSimulationClock().isRunning());
        Assertions.assertEquals(1_000L, session.getSimulationClock().currentSimTimeMillis());
    }

    /**
     * 验证暂停命令会冻结仿真时间并把会话切到暂停态。
     */
    @Test
    void shouldPauseRunningSession() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        SimulationControlService controlService = new SimulationControlService(sessionManager);
        controlService.handleStart("instance-001", new ProtocolData());
        wallClock.set(1_500L);

        controlService.handlePause("instance-001", new ProtocolData());
        long pausedSimTime = session.getSimulationClock().currentSimTimeMillis();
        wallClock.set(1_800L);

        Assertions.assertEquals(SimulationSessionState.PAUSED, session.getState());
        Assertions.assertFalse(session.getSimulationClock().isRunning());
        Assertions.assertEquals(1_500L, pausedSimTime);
        Assertions.assertEquals(pausedSimTime, session.getSimulationClock().currentSimTimeMillis());
    }

    /**
     * 验证继续命令会恢复仿真时间推进。
     */
    @Test
    void shouldResumePausedSession() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        SimulationControlService controlService = new SimulationControlService(sessionManager);
        controlService.handleStart("instance-001", new ProtocolData());
        wallClock.set(1_500L);
        controlService.handlePause("instance-001", new ProtocolData());
        wallClock.set(2_000L);

        controlService.handleResume("instance-001", new ProtocolData());
        wallClock.set(2_300L);

        Assertions.assertEquals(SimulationSessionState.RUNNING, session.getState());
        Assertions.assertTrue(session.getSimulationClock().isRunning());
        Assertions.assertEquals(1_800L, session.getSimulationClock().currentSimTimeMillis());
    }

    /**
     * 验证暂停后的启动命令会按恢复处理，重复控制命令保持幂等。
     */
    @Test
    void shouldHandleIdempotentCommandsSafely() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        SimulationControlService controlService = new SimulationControlService(sessionManager);

        controlService.handleStart("instance-001", new ProtocolData());
        controlService.handleStart("instance-001", new ProtocolData());
        wallClock.set(1_200L);
        controlService.handlePause("instance-001", new ProtocolData());
        controlService.handlePause("instance-001", new ProtocolData());
        wallClock.set(1_500L);
        controlService.handleStart("instance-001", new ProtocolData());
        controlService.handleResume("instance-001", new ProtocolData());
        controlService.handleResume("missing-instance", new ProtocolData());

        Assertions.assertEquals(SimulationSessionState.RUNNING, session.getState());
        Assertions.assertEquals(1_200L, session.getSimulationClock().currentSimTimeMillis());
    }
}

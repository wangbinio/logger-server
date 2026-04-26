package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.clock.SimulationClock;
import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.TimeControlRecordCommand;
import com.szzh.loggerserver.util.ProtocolData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SimulationControlService controlService = new SimulationControlService(sessionManager, writeService);

        controlService.handleStart("instance-001", buildProtocolData(11, 2100, 7));

        Assertions.assertEquals(SimulationSessionState.RUNNING, session.getState());
        Assertions.assertTrue(session.getSimulationClock().isInitialized());
        Assertions.assertTrue(session.getSimulationClock().isRunning());
        Assertions.assertEquals(1_000L, session.getSimulationClock().currentSimTimeMillis());
        TimeControlRecordCommand command = captureTimeControlCommand(writeService);
        Assertions.assertEquals("instance-001", command.getInstanceId());
        Assertions.assertEquals(1_000L, command.getSimTime());
        Assertions.assertEquals(1D, command.getRate());
        Assertions.assertEquals(11, command.getSenderId());
        Assertions.assertEquals(2100, command.getMessageType());
        Assertions.assertEquals(7, command.getMessageCode());
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
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SimulationControlService controlService = new SimulationControlService(sessionManager, writeService);
        controlService.handleStart("instance-001", buildProtocolData(11, 2100, 7));
        wallClock.set(1_500L);

        controlService.handlePause("instance-001", buildProtocolData(12, 2100, 8));
        long pausedSimTime = session.getSimulationClock().currentSimTimeMillis();
        wallClock.set(1_800L);

        Assertions.assertEquals(SimulationSessionState.PAUSED, session.getState());
        Assertions.assertFalse(session.getSimulationClock().isRunning());
        Assertions.assertEquals(1_500L, pausedSimTime);
        Assertions.assertEquals(pausedSimTime, session.getSimulationClock().currentSimTimeMillis());
        ArgumentCaptor<TimeControlRecordCommand> commandCaptor =
                ArgumentCaptor.forClass(TimeControlRecordCommand.class);
        Mockito.verify(writeService, Mockito.times(2)).writeTimeControl(commandCaptor.capture());
        TimeControlRecordCommand pauseCommand = commandCaptor.getAllValues().get(1);
        Assertions.assertEquals(1_500L, pauseCommand.getSimTime());
        Assertions.assertEquals(0D, pauseCommand.getRate());
        Assertions.assertEquals(12, pauseCommand.getSenderId());
        Assertions.assertEquals(2100, pauseCommand.getMessageType());
        Assertions.assertEquals(8, pauseCommand.getMessageCode());
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
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SimulationControlService controlService = new SimulationControlService(sessionManager, writeService);
        controlService.handleStart("instance-001", buildProtocolData(11, 2100, 7));
        wallClock.set(1_500L);
        controlService.handlePause("instance-001", buildProtocolData(12, 2100, 8));
        wallClock.set(2_000L);

        controlService.handleResume("instance-001", buildProtocolData(13, 2100, 9));
        wallClock.set(2_300L);

        Assertions.assertEquals(SimulationSessionState.RUNNING, session.getState());
        Assertions.assertTrue(session.getSimulationClock().isRunning());
        Assertions.assertEquals(1_800L, session.getSimulationClock().currentSimTimeMillis());
        ArgumentCaptor<TimeControlRecordCommand> commandCaptor =
                ArgumentCaptor.forClass(TimeControlRecordCommand.class);
        Mockito.verify(writeService, Mockito.times(3)).writeTimeControl(commandCaptor.capture());
        TimeControlRecordCommand resumeCommand = commandCaptor.getAllValues().get(2);
        Assertions.assertEquals(1_500L, resumeCommand.getSimTime());
        Assertions.assertEquals(1D, resumeCommand.getRate());
        Assertions.assertEquals(13, resumeCommand.getSenderId());
        Assertions.assertEquals(2100, resumeCommand.getMessageType());
        Assertions.assertEquals(9, resumeCommand.getMessageCode());
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
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SimulationControlService controlService = new SimulationControlService(sessionManager, writeService);

        controlService.handleStart("instance-001", buildProtocolData(11, 2100, 7));
        controlService.handleStart("instance-001", buildProtocolData(11, 2100, 7));
        wallClock.set(1_200L);
        controlService.handlePause("instance-001", buildProtocolData(12, 2100, 8));
        controlService.handlePause("instance-001", buildProtocolData(12, 2100, 8));
        wallClock.set(1_500L);
        controlService.handleStart("instance-001", buildProtocolData(13, 2100, 9));
        controlService.handleResume("instance-001", buildProtocolData(13, 2100, 9));
        controlService.handleResume("missing-instance", buildProtocolData(13, 2100, 9));

        Assertions.assertEquals(SimulationSessionState.RUNNING, session.getState());
        Assertions.assertEquals(1_200L, session.getSimulationClock().currentSimTimeMillis());
        Mockito.verify(writeService, Mockito.times(3)).writeTimeControl(Mockito.any(TimeControlRecordCommand.class));
    }

    /**
     * 验证控制时间点写入失败不会阻断状态迁移。
     */
    @Test
    void shouldKeepControlFlowSuccessfulWhenTimeControlWriteFails() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SimulationControlService controlService = new SimulationControlService(sessionManager, writeService);
        Mockito.doThrow(new IllegalStateException("write boom"))
                .when(writeService)
                .writeTimeControl(Mockito.any(TimeControlRecordCommand.class));

        controlService.handleStart("instance-001", buildProtocolData(11, 2100, 7));

        Assertions.assertEquals(SimulationSessionState.RUNNING, session.getState());
        Assertions.assertNull(session.getLastErrorMessage());
    }

    /**
     * 捕获唯一一条控制时间点写入命令。
     *
     * @param writeService 写入服务。
     * @return 控制时间点写入命令。
     */
    private TimeControlRecordCommand captureTimeControlCommand(TdengineWriteService writeService) {
        ArgumentCaptor<TimeControlRecordCommand> commandCaptor =
                ArgumentCaptor.forClass(TimeControlRecordCommand.class);
        Mockito.verify(writeService).writeTimeControl(commandCaptor.capture());
        return commandCaptor.getValue();
    }

    /**
     * 构造协议数据。
     *
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @return 协议数据。
     */
    private ProtocolData buildProtocolData(int senderId, int messageType, int messageCode) {
        ProtocolData protocolData = new ProtocolData();
        protocolData.setSenderId(senderId);
        protocolData.setMessageType(messageType);
        protocolData.setMessageCode(messageCode);
        return protocolData;
    }
}

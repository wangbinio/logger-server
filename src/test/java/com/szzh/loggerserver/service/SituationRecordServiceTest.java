package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.clock.SimulationClock;
import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.SituationRecordCommand;
import com.szzh.loggerserver.util.ProtocolData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 态势记录服务测试。
 */
class SituationRecordServiceTest {

    /**
     * 验证运行态消息会被转换为写库命令并更新统计。
     */
    @Test
    void shouldWriteRecordWhenSessionRunning() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SituationRecordService recordService = new SituationRecordService(sessionManager, writeService);
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        session.getSimulationClock().start();
        session.updateState(SimulationSessionState.RUNNING);
        wallClock.set(1_250L);

        recordService.handle("instance-001", buildProtocolData());

        ArgumentCaptor<SituationRecordCommand> commandCaptor = ArgumentCaptor.forClass(SituationRecordCommand.class);
        Mockito.verify(writeService).write(commandCaptor.capture());
        SituationRecordCommand command = commandCaptor.getValue();
        Assertions.assertEquals("instance-001", command.getInstanceId());
        Assertions.assertEquals(7, command.getSenderId());
        Assertions.assertEquals(2100, command.getMessageType());
        Assertions.assertEquals(9, command.getMessageCode());
        Assertions.assertEquals(1_250L, command.getSimTime());
        Assertions.assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), command.getRawData());
        Assertions.assertEquals(1L, session.receivedMessageCount());
        Assertions.assertEquals(1L, session.writtenRecordCount());
        Assertions.assertEquals(0L, session.droppedMessageCount());
    }

    /**
     * 验证不存在会话时直接丢弃态势消息。
     */
    @Test
    void shouldDropWhenSessionMissing() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SituationRecordService recordService = new SituationRecordService(sessionManager, writeService);

        recordService.handle("missing-instance", buildProtocolData());

        Mockito.verify(writeService, Mockito.never()).write(Mockito.any(SituationRecordCommand.class));
    }

    /**
     * 验证非运行态消息会计数后丢弃，不进入写库。
     */
    @Test
    void shouldDropWhenSessionNotRunning() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SituationRecordService recordService = new SituationRecordService(sessionManager, writeService);
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);

        recordService.handle("instance-001", buildProtocolData());

        Mockito.verify(writeService, Mockito.never()).write(Mockito.any(SituationRecordCommand.class));
        Assertions.assertEquals(1L, session.receivedMessageCount());
        Assertions.assertEquals(1L, session.droppedMessageCount());
        Assertions.assertEquals(0L, session.writtenRecordCount());
    }

    /**
     * 验证写库失败时会记录异常并继续向上抛出。
     */
    @Test
    void shouldRecordFailureWhenWriteFails() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SituationRecordService recordService = new SituationRecordService(sessionManager, writeService);
        SimulationSession session = sessionManager.createSession("instance-001");
        session.updateState(SimulationSessionState.READY);
        session.getSimulationClock().start();
        session.updateState(SimulationSessionState.RUNNING);
        Mockito.doThrow(new IllegalStateException("write boom"))
                .when(writeService)
                .write(Mockito.any(SituationRecordCommand.class));

        Assertions.assertThrows(IllegalStateException.class,
                () -> recordService.handle("instance-001", buildProtocolData()));
        Assertions.assertEquals("write boom", session.getLastErrorMessage());
        Assertions.assertEquals(1L, session.receivedMessageCount());
        Assertions.assertEquals(0L, session.writtenRecordCount());
    }

    /**
     * 构造态势协议对象。
     *
     * @return 协议对象。
     */
    private ProtocolData buildProtocolData() {
        ProtocolData protocolData = new ProtocolData();
        protocolData.setSenderId(7);
        protocolData.setMessageType(2100);
        protocolData.setMessageCode(9);
        protocolData.setRawData("payload".getBytes(StandardCharsets.UTF_8));
        return protocolData;
    }
}

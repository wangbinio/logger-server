package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.SituationRecordCommand;
import com.szzh.loggerserver.mq.SituationRecordIngressPort;
import com.szzh.loggerserver.util.ProtocolData;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 态势记录服务。
 */
@Service
public class SituationRecordService implements SituationRecordIngressPort {

    private final SimulationSessionManager sessionManager;

    private final TdengineWriteService writeService;

    /**
     * 创建态势记录服务。
     *
     * @param sessionManager 会话管理器。
     * @param writeService TDengine 写入服务。
     */
    public SituationRecordService(SimulationSessionManager sessionManager,
                                  TdengineWriteService writeService) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.writeService = Objects.requireNonNull(writeService, "writeService 不能为空");
    }

    /**
     * 处理态势消息。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handle(String instanceId, ProtocolData protocolData) {
        Objects.requireNonNull(protocolData, "protocolData 不能为空");
        Optional<SimulationSession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            return;
        }

        SimulationSession session = sessionOptional.get();
        session.markMessageReceived();
        if (session.getState() != SimulationSessionState.RUNNING) {
            session.markMessageDropped();
            return;
        }

        try {
            SituationRecordCommand command = buildRecordCommand(session, protocolData);
            writeService.write(command);
            session.markRecordWritten();
        } catch (RuntimeException exception) {
            session.recordFailure(exception.getMessage());
            throw exception;
        }
    }

    /**
     * 构造态势写库命令。
     *
     * @param session 仿真实例会话。
     * @param protocolData 协议数据。
     * @return 写库命令。
     */
    private SituationRecordCommand buildRecordCommand(SimulationSession session, ProtocolData protocolData) {
        return SituationRecordCommand.builder()
                .instanceId(session.getInstanceId())
                .senderId(protocolData.getSenderId())
                .messageType(protocolData.getMessageType())
                .messageCode(protocolData.getMessageCode())
                .simTime(session.getSimulationClock().currentSimTimeMillis())
                .rawData(protocolData.getRawData())
                .build();
    }
}

package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.SituationRecordCommand;
import com.szzh.loggerserver.mq.SituationRecordIngressPort;
import com.szzh.common.exception.BusinessException;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.common.protocol.ProtocolData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 态势记录服务。
 */
@Service
public class SituationRecordService implements SituationRecordIngressPort {

    private static final Logger log = LoggerFactory.getLogger(SituationRecordService.class);

    private final SimulationSessionManager sessionManager;

    private final TdengineWriteService writeService;

    private final LoggerMetrics loggerMetrics;

    /**
     * 创建态势记录服务。
     *
     * @param sessionManager 会话管理器。
     * @param writeService TDengine 写入服务。
     */
    public SituationRecordService(SimulationSessionManager sessionManager,
                                  TdengineWriteService writeService) {
        this(sessionManager, writeService, new LoggerMetrics());
    }

    /**
     * 创建态势记录服务。
     *
     * @param sessionManager 会话管理器。
     * @param writeService TDengine 写入服务。
     * @param loggerMetrics 日志指标封装。
     */
    @Autowired
    public SituationRecordService(SimulationSessionManager sessionManager,
                                  TdengineWriteService writeService,
                                  LoggerMetrics loggerMetrics) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.writeService = Objects.requireNonNull(writeService, "writeService 不能为空");
        this.loggerMetrics = Objects.requireNonNull(loggerMetrics, "loggerMetrics 不能为空");
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
            loggerMetrics.recordStateViolation();

            logRecordResult("ignored_missing_session", instanceId, protocolData, -1L, "MISSING");
            return;
        }

        SimulationSession session = sessionOptional.get();
        session.markMessageReceived();
        loggerMetrics.recordMessageReceived();
        if (session.getState() != SimulationSessionState.RUNNING) {
            session.markMessageDropped();
            loggerMetrics.recordMessageDropped();
            loggerMetrics.recordStateViolation();

            logRecordResult("drop_not_running", instanceId, protocolData, currentSimTimeOrDefault(session), session.getState().name());
            return;
        }

        try {
            SituationRecordCommand command = buildRecordCommand(session, protocolData);
            writeService.write(command);
            session.markRecordWritten();
            loggerMetrics.recordMessageWritten();

            logRecordResult("write_success", instanceId, protocolData, command.getSimTime(), session.getState().name());
        } catch (RuntimeException exception) {
            session.recordFailure(exception.getMessage());
            loggerMetrics.recordTdengineWriteFailure();

            logRecordFailed(instanceId, protocolData, session, exception);
            throw wrapWriteException(exception);
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

    /**
     * 获取当前仿真时间，未初始化时返回 -1。
     *
     * @param session 会话对象。
     * @return 当前仿真时间。
     */
    private long currentSimTimeOrDefault(SimulationSession session) {
        try {
            return session.getSimulationClock().currentSimTimeMillis();
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    /**
     * 包装写库异常，统一暴露为业务异常。
     *
     * @param exception 原始异常。
     * @return 业务异常。
     */
    private RuntimeException wrapWriteException(RuntimeException exception) {
        if (exception instanceof BusinessException
                && ((BusinessException) exception).getCategory() == BusinessException.Category.TDENGINE_WRITE) {
            return exception;
        }
        return BusinessException.tdengineWrite("TDengine 写入失败", exception);
    }

    /**
     * 输出态势记录处理结果日志。
     *
     * @param result 处理结果。
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     * @param simTime 仿真时间。
     * @param sessionState 会话状态。
     */
    private void logRecordResult(String result,
                                 String instanceId,
                                 ProtocolData protocolData,
                                 long simTime,
                                 String sessionState) {
        log.info("result={} instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={}",
                result, instanceId, protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), simTime, sessionState);
    }

    /**
     * 输出态势记录写入失败日志。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     * @param session 会话对象。
     * @param exception 写入异常。
     */
    private void logRecordFailed(String instanceId,
                                 ProtocolData protocolData,
                                 SimulationSession session,
                                 RuntimeException exception) {
        log.error("result=write_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={} reason={}",
                instanceId, protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), currentSimTimeOrDefault(session), session.getState(), exception.getMessage(), exception);
    }
}

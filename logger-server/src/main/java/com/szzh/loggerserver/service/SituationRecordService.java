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
            log.info("result=ignored_missing_session instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState=MISSING",
                    instanceId,
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId());
            return;
        }

        SimulationSession session = sessionOptional.get();
        session.markMessageReceived();
        loggerMetrics.recordMessageReceived();
        if (session.getState() != SimulationSessionState.RUNNING) {
            session.markMessageDropped();
            loggerMetrics.recordMessageDropped();
            loggerMetrics.recordStateViolation();
            log.info("result=drop_not_running instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={}",
                    instanceId,
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    currentSimTimeOrDefault(session),
                    session.getState());
            return;
        }

        try {
            SituationRecordCommand command = buildRecordCommand(session, protocolData);
            writeService.write(command);
            session.markRecordWritten();
            loggerMetrics.recordMessageWritten();
            log.info("result=write_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={}",
                    instanceId,
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    command.getSimTime(),
                    session.getState());
        } catch (RuntimeException exception) {
            session.recordFailure(exception.getMessage());
            loggerMetrics.recordTdengineWriteFailure();
            log.error("result=write_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={} reason={}",
                    instanceId,
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    currentSimTimeOrDefault(session),
                    session.getState(),
                    exception.getMessage(),
                    exception);
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
}

package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.TaskCreatePayload;
import com.szzh.loggerserver.mq.SimulationLifecycleCommandPort;
import com.szzh.loggerserver.mq.TopicSubscriptionManager;
import com.szzh.loggerserver.support.exception.BusinessException;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.loggerserver.util.ProtocolData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 仿真生命周期服务。
 */
@Service
public class SimulationLifecycleService implements SimulationLifecycleCommandPort {

    private static final Logger log = LoggerFactory.getLogger(SimulationLifecycleService.class);

    private final SimulationSessionManager sessionManager;

    private final TdengineSchemaService schemaService;

    private final TopicSubscriptionManager subscriptionManager;

    private final LoggerMetrics loggerMetrics;

    /**
     * 创建仿真生命周期服务。
     *
     * @param sessionManager 会话管理器。
     * @param schemaService TDengine 建表服务。
     * @param subscriptionManager 动态订阅管理器。
     */
    public SimulationLifecycleService(SimulationSessionManager sessionManager,
                                      TdengineSchemaService schemaService,
                                      TopicSubscriptionManager subscriptionManager) {
        this(sessionManager, schemaService, subscriptionManager, new LoggerMetrics());
    }

    /**
     * 创建仿真生命周期服务。
     *
     * @param sessionManager 会话管理器。
     * @param schemaService TDengine 建表服务。
     * @param subscriptionManager 动态订阅管理器。
     * @param loggerMetrics 日志指标封装。
     */
    @Autowired
    public SimulationLifecycleService(SimulationSessionManager sessionManager,
                                      TdengineSchemaService schemaService,
                                      TopicSubscriptionManager subscriptionManager,
                                      LoggerMetrics loggerMetrics) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.schemaService = Objects.requireNonNull(schemaService, "schemaService 不能为空");
        this.subscriptionManager = Objects.requireNonNull(subscriptionManager, "subscriptionManager 不能为空");
        this.loggerMetrics = Objects.requireNonNull(loggerMetrics, "loggerMetrics 不能为空");
    }

    /**
     * 处理创建命令。
     *
     * @param protocolData 协议数据。
     */
    @Override
    public void handleCreate(ProtocolData protocolData) {
        TaskCreatePayload payload = TaskCreatePayload.fromProtocolData(protocolData);
        Optional<SimulationSession> existingSession = sessionManager.getSession(payload.getInstanceId());
        if (existingSession.isPresent() && existingSession.get().getState() != SimulationSessionState.STOPPED) {
            loggerMetrics.recordStateViolation();
            log.info("result=ignored_duplicate_create instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState={}",
                    payload.getInstanceId(),
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    existingSession.get().getState());
            return;
        }

        SimulationSession session = sessionManager.createSession(payload.getInstanceId());
        try {
            schemaService.createStableIfAbsent(session.getInstanceId());
            subscriptionManager.subscribe(session.getInstanceId());
            session.updateState(SimulationSessionState.READY);
            loggerMetrics.setActiveSessionCount(sessionManager.size());
            log.info("result=create_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState={}",
                    session.getInstanceId(),
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    session.getState());
        } catch (RuntimeException exception) {
            markSessionFailed(session, exception);
            loggerMetrics.setActiveSessionCount(sessionManager.size());
            log.error("result=create_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState={} reason={}",
                    session.getInstanceId(),
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    session.getState(),
                    exception.getMessage(),
                    exception);
            throw exception;
        }
    }

    /**
     * 处理停止命令。
     *
     * @param protocolData 协议数据。
     */
    @Override
    public void handleStop(ProtocolData protocolData) {
        TaskCreatePayload payload = TaskCreatePayload.fromProtocolData(protocolData);
        Optional<SimulationSession> sessionOptional = sessionManager.getSession(payload.getInstanceId());
        if (!sessionOptional.isPresent()) {
            loggerMetrics.recordStateViolation();
            log.info("result=ignored_missing_stop instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState=MISSING",
                    payload.getInstanceId(),
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId());
            return;
        }

        SimulationSession session = sessionOptional.get();
        subscriptionManager.unsubscribe(session.getInstanceId());
        sessionManager.stopSession(session.getInstanceId());
        sessionManager.removeSession(session.getInstanceId());
        loggerMetrics.setActiveSessionCount(sessionManager.size());
        log.info("result=stop_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState=STOPPED",
                session.getInstanceId(),
                protocolData.getMessageType(),
                protocolData.getMessageCode(),
                protocolData.getSenderId());
    }

    /**
     * 把会话标记为失败态并记录异常。
     *
     * @param session 会话对象。
     * @param exception 运行时异常。
     */
    private void markSessionFailed(SimulationSession session, RuntimeException exception) {
        session.recordFailure(exception.getMessage());
        session.updateState(SimulationSessionState.FAILED);
        if (exception instanceof BusinessException
                && ((BusinessException) exception).getCategory() == BusinessException.Category.TDENGINE_WRITE) {
            loggerMetrics.recordTdengineWriteFailure();
        }
    }
}

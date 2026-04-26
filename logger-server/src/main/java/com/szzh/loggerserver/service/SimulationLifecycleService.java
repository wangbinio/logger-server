package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.TaskCreatePayload;
import com.szzh.loggerserver.model.dto.TimeControlRecordCommand;
import com.szzh.loggerserver.mq.SimulationLifecycleCommandPort;
import com.szzh.loggerserver.mq.TopicSubscriptionManager;
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
 * 仿真生命周期服务。
 */
@Service
public class SimulationLifecycleService implements SimulationLifecycleCommandPort {

    private static final Logger log = LoggerFactory.getLogger(SimulationLifecycleService.class);

    private final SimulationSessionManager sessionManager;

    private final TdengineSchemaService schemaService;

    private final TopicSubscriptionManager subscriptionManager;

    private final LoggerMetrics loggerMetrics;

    private final TdengineWriteService tdengineWriteService;

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
        this(sessionManager, schemaService, subscriptionManager, new LoggerMetrics(), null);
    }

    /**
     * 创建仿真生命周期服务。
     *
     * @param sessionManager 会话管理器。
     * @param schemaService TDengine 建表服务。
     * @param subscriptionManager 动态订阅管理器。
     * @param tdengineWriteService TDengine 写入服务。
     */
    public SimulationLifecycleService(SimulationSessionManager sessionManager,
                                      TdengineSchemaService schemaService,
                                      TopicSubscriptionManager subscriptionManager,
                                      TdengineWriteService tdengineWriteService) {
        this(sessionManager, schemaService, subscriptionManager, new LoggerMetrics(), tdengineWriteService);
    }

    /**
     * 创建仿真生命周期服务。
     *
     * @param sessionManager 会话管理器。
     * @param schemaService TDengine 建表服务。
     * @param subscriptionManager 动态订阅管理器。
     * @param loggerMetrics 日志指标封装。
     */
    public SimulationLifecycleService(SimulationSessionManager sessionManager,
                                      TdengineSchemaService schemaService,
                                      TopicSubscriptionManager subscriptionManager,
                                      LoggerMetrics loggerMetrics) {
        this(sessionManager, schemaService, subscriptionManager, loggerMetrics, null);
    }

    /**
     * 创建仿真生命周期服务。
     *
     * @param sessionManager 会话管理器。
     * @param schemaService TDengine 建表服务。
     * @param subscriptionManager 动态订阅管理器。
     * @param loggerMetrics 日志指标封装。
     * @param tdengineWriteService TDengine 写入服务。
     */
    @Autowired
    public SimulationLifecycleService(SimulationSessionManager sessionManager,
                                      TdengineSchemaService schemaService,
                                      TopicSubscriptionManager subscriptionManager,
                                      LoggerMetrics loggerMetrics,
                                      TdengineWriteService tdengineWriteService) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.schemaService = Objects.requireNonNull(schemaService, "schemaService 不能为空");
        this.subscriptionManager = Objects.requireNonNull(subscriptionManager, "subscriptionManager 不能为空");
        this.loggerMetrics = Objects.requireNonNull(loggerMetrics, "loggerMetrics 不能为空");
        this.tdengineWriteService = tdengineWriteService;
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

            logLifecycleResult("ignored_duplicate_create", payload.getInstanceId(), protocolData, existingSession.get().getState().name());
            return;
        }

        SimulationSession session = sessionManager.createSession(payload.getInstanceId());
        try {
            schemaService.createStableIfAbsent(session.getInstanceId());
            schemaService.createTimeControlTableIfAbsent(session.getInstanceId());
            subscriptionManager.subscribe(session.getInstanceId());
            session.updateState(SimulationSessionState.READY);
            loggerMetrics.setActiveSessionCount(sessionManager.size());

            logLifecycleResult("create_success", session.getInstanceId(), protocolData, session.getState().name());
        } catch (RuntimeException exception) {
            markSessionFailed(session, exception);
            loggerMetrics.setActiveSessionCount(sessionManager.size());

            logLifecycleFailed("create_failed", session.getInstanceId(), protocolData, session.getState().name(), exception);
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

            logLifecycleResult("ignored_missing_stop", payload.getInstanceId(), protocolData, "MISSING");
            return;
        }

        SimulationSession session = sessionOptional.get();
        recordStopTimeControlQuietly(session, protocolData);
        subscriptionManager.unsubscribe(session.getInstanceId());
        sessionManager.stopSession(session.getInstanceId());
        sessionManager.removeSession(session.getInstanceId());
        loggerMetrics.setActiveSessionCount(sessionManager.size());

        logLifecycleResult("stop_success", session.getInstanceId(), protocolData, SimulationSessionState.STOPPED.name());
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

    /**
     * 记录停止时间点，写入失败只记录日志，不阻断停止流程。
     *
     * @param session 会话对象。
     * @param protocolData 协议数据。
     */
    private void recordStopTimeControlQuietly(SimulationSession session, ProtocolData protocolData) {
        if (tdengineWriteService == null) {
            return;
        }
        long simTime = resolveStopSimTime(session);
        TimeControlRecordCommand command = TimeControlRecordCommand.builder()
                .instanceId(session.getInstanceId())
                .simTime(simTime)
                .rate(0D)
                .senderId(protocolData.getSenderId())
                .messageType(protocolData.getMessageType())
                .messageCode(protocolData.getMessageCode())
                .build();
        try {
            tdengineWriteService.writeTimeControl(command);

            logStopTimeControlWriteSuccess(command);
        } catch (RuntimeException exception) {
            logStopTimeControlWriteFailed(command, session, exception);
        }
    }

    /**
     * 获取停止时刻的仿真时间。
     *
     * @param session 会话对象。
     * @return 停止时刻仿真时间。
     */
    private long resolveStopSimTime(SimulationSession session) {
        if (!session.getSimulationClock().isInitialized()) {
            return 0L;
        }
        return session.getSimulationClock().currentSimTimeMillis();
    }

    /**
     * 输出生命周期处理结果日志。
     *
     * @param result 处理结果。
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     * @param sessionState 会话状态。
     */
    private void logLifecycleResult(String result,
                                    String instanceId,
                                    ProtocolData protocolData,
                                    String sessionState) {
        log.info("result={} instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState={}",
                result, instanceId, protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), sessionState);
    }

    /**
     * 输出生命周期失败日志。
     *
     * @param result 处理结果。
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     * @param sessionState 会话状态。
     * @param exception 运行时异常。
     */
    private void logLifecycleFailed(String result,
                                    String instanceId,
                                    ProtocolData protocolData,
                                    String sessionState,
                                    RuntimeException exception) {
        log.error("result={} instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState={} reason={}",
                result, instanceId, protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), sessionState, exception.getMessage(), exception);
    }

    /**
     * 输出停止时间点写入成功日志。
     *
     * @param command 控制时间点写入命令。
     */
    private void logStopTimeControlWriteSuccess(TimeControlRecordCommand command) {
        log.debug("result=time_control_stop_write_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} rate={}",
                command.getInstanceId(), command.getMessageType(), command.getMessageCode(), command.getSenderId(), command.getSimTime(), command.getRate());
    }

    /**
     * 输出停止时间点写入失败日志。
     *
     * @param command 控制时间点写入命令。
     * @param session 会话对象。
     * @param exception 写入异常。
     */
    private void logStopTimeControlWriteFailed(TimeControlRecordCommand command,
                                               SimulationSession session,
                                               RuntimeException exception) {
        log.error("result=time_control_stop_write_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} rate={} sessionState={} reason={}",
                command.getInstanceId(), command.getMessageType(), command.getMessageCode(), command.getSenderId(), command.getSimTime(), command.getRate(), session.getState(), exception.getMessage(), exception);
    }
}

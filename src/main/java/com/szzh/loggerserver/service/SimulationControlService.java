package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.TimeControlRecordCommand;
import com.szzh.loggerserver.mq.SimulationControlCommandPort;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.loggerserver.util.ProtocolData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 仿真控制服务。
 */
@Service
public class SimulationControlService implements SimulationControlCommandPort {

    private static final Logger log = LoggerFactory.getLogger(SimulationControlService.class);

    private final SimulationSessionManager sessionManager;

    private final LoggerMetrics loggerMetrics;

    private final TdengineWriteService tdengineWriteService;

    /**
     * 创建仿真控制服务。
     *
     * @param sessionManager 会话管理器。
     */
    public SimulationControlService(SimulationSessionManager sessionManager) {
        this(sessionManager, new LoggerMetrics(), null);
    }

    /**
     * 创建仿真控制服务。
     *
     * @param sessionManager 会话管理器。
     * @param tdengineWriteService TDengine 写入服务。
     */
    public SimulationControlService(SimulationSessionManager sessionManager,
                                    TdengineWriteService tdengineWriteService) {
        this(sessionManager, new LoggerMetrics(), tdengineWriteService);
    }

    /**
     * 创建仿真控制服务。
     *
     * @param sessionManager 会话管理器。
     * @param loggerMetrics 日志指标封装。
     */
    public SimulationControlService(SimulationSessionManager sessionManager,
                                    LoggerMetrics loggerMetrics) {
        this(sessionManager, loggerMetrics, null);
    }

    /**
     * 创建仿真控制服务。
     *
     * @param sessionManager 会话管理器。
     * @param loggerMetrics 日志指标封装。
     * @param tdengineWriteService TDengine 写入服务。
     */
    @Autowired
    public SimulationControlService(SimulationSessionManager sessionManager,
                                    LoggerMetrics loggerMetrics,
                                    TdengineWriteService tdengineWriteService) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.loggerMetrics = Objects.requireNonNull(loggerMetrics, "loggerMetrics 不能为空");
        this.tdengineWriteService = tdengineWriteService;
    }

    /**
     * 处理开始命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handleStart(String instanceId, ProtocolData protocolData) {
        Optional<SimulationSession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            logStateIgnore("ignored_missing_start", instanceId, protocolData, "MISSING");
            return;
        }
        SimulationSession session = sessionOptional.get();
        if (session.getState() == SimulationSessionState.RUNNING) {
            logStateIgnore("ignored_duplicate_start", instanceId, protocolData, session.getState().name());
            return;
        }
        if (session.getState() == SimulationSessionState.READY) {
            executeTransition(session, SimulationSessionState.RUNNING, new SessionAction() {
                /**
                 * 执行时钟启动。
                 */
                @Override
                public void apply() {
                    session.getSimulationClock().start();
                }
            });
            recordTimeControlQuietly(session, protocolData, 1D);
            log.info("result=start_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={}",
                    instanceId,
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    session.getSimulationClock().currentSimTimeMillis(),
                    session.getState());
            return;
        }
        if (session.getState() == SimulationSessionState.PAUSED) {
            handleResume(instanceId, protocolData);
            return;
        }
        logStateIgnore("ignored_invalid_start_state", instanceId, protocolData, session.getState().name());
    }

    /**
     * 处理暂停命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handlePause(String instanceId, ProtocolData protocolData) {
        Optional<SimulationSession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            logStateIgnore("ignored_missing_pause", instanceId, protocolData, "MISSING");
            return;
        }
        SimulationSession session = sessionOptional.get();
        if (session.getState() != SimulationSessionState.RUNNING) {
            logStateIgnore("ignored_invalid_pause_state", instanceId, protocolData, session.getState().name());
            return;
        }
        executeTransition(session, SimulationSessionState.PAUSED, new SessionAction() {
            /**
             * 执行时钟暂停。
             */
            @Override
            public void apply() {
                session.getSimulationClock().pause();
            }
        });
        recordTimeControlQuietly(session, protocolData, 0D);
        log.info("result=pause_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={}",
                instanceId,
                protocolData.getMessageType(),
                protocolData.getMessageCode(),
                protocolData.getSenderId(),
                session.getSimulationClock().currentSimTimeMillis(),
                session.getState());
    }

    /**
     * 处理继续命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    @Override
    public void handleResume(String instanceId, ProtocolData protocolData) {
        Optional<SimulationSession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            logStateIgnore("ignored_missing_resume", instanceId, protocolData, "MISSING");
            return;
        }
        SimulationSession session = sessionOptional.get();
        if (session.getState() == SimulationSessionState.RUNNING) {
            logStateIgnore("ignored_duplicate_resume", instanceId, protocolData, session.getState().name());
            return;
        }
        if (session.getState() != SimulationSessionState.PAUSED) {
            logStateIgnore("ignored_invalid_resume_state", instanceId, protocolData, session.getState().name());
            return;
        }
        executeTransition(session, SimulationSessionState.RUNNING, new SessionAction() {
            /**
             * 执行时钟恢复。
             */
            @Override
            public void apply() {
                session.getSimulationClock().resume();
            }
        });
        recordTimeControlQuietly(session, protocolData, 1D);
        log.info("result=resume_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={}",
                instanceId,
                protocolData.getMessageType(),
                protocolData.getMessageCode(),
                protocolData.getSenderId(),
                session.getSimulationClock().currentSimTimeMillis(),
                session.getState());
    }

    /**
     * 执行状态迁移，并在失败时记录异常。
     *
     * @param session 会话对象。
     * @param nextState 目标状态。
     * @param sessionAction 时钟动作。
     */
    private void executeTransition(SimulationSession session,
                                   SimulationSessionState nextState,
                                   SessionAction sessionAction) {
        try {
            sessionAction.apply();
            session.updateState(nextState);
        } catch (RuntimeException exception) {
            session.recordFailure(exception.getMessage());
            throw exception;
        }
    }

    /**
     * 记录控制时间点，写入失败只记录日志，不阻断控制流程。
     *
     * @param session 仿真实例会话。
     * @param protocolData 协议数据。
     * @param rate 控制命令生效后的回放倍率。
     */
    private void recordTimeControlQuietly(SimulationSession session,
                                          ProtocolData protocolData,
                                          double rate) {
        if (tdengineWriteService == null) {
            return;
        }
        long simTime = session.getSimulationClock().currentSimTimeMillis();
        TimeControlRecordCommand command = TimeControlRecordCommand.builder()
                .instanceId(session.getInstanceId())
                .simTime(simTime)
                .rate(rate)
                .senderId(protocolData.getSenderId())
                .messageType(protocolData.getMessageType())
                .messageCode(protocolData.getMessageCode())
                .build();
        try {
            tdengineWriteService.writeTimeControl(command);
            log.debug("result=time_control_write_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} rate={}",
                    command.getInstanceId(),
                    command.getMessageType(),
                    command.getMessageCode(),
                    command.getSenderId(),
                    command.getSimTime(),
                    command.getRate());
        } catch (RuntimeException exception) {
            log.error("result=time_control_write_failed instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} rate={} sessionState={} reason={}",
                    command.getInstanceId(),
                    command.getMessageType(),
                    command.getMessageCode(),
                    command.getSenderId(),
                    command.getSimTime(),
                    command.getRate(),
                    session.getState(),
                    exception.getMessage(),
                    exception);
        }
    }

    /**
     * 记录状态忽略日志和指标。
     *
     * @param result 处理结果。
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     * @param sessionState 会话状态。
     */
    private void logStateIgnore(String result,
                                String instanceId,
                                ProtocolData protocolData,
                                String sessionState) {
        loggerMetrics.recordStateViolation();
        log.info("result={} instanceId={} topic=- messageType={} messageCode={} senderId={} simtime=-1 sessionState={}",
                result,
                instanceId,
                protocolData.getMessageType(),
                protocolData.getMessageCode(),
                protocolData.getSenderId(),
                sessionState);
    }

    /**
     * 会话状态动作。
     */
    private interface SessionAction {

        /**
         * 执行动作。
         */
        void apply();
    }
}

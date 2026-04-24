package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.mq.SimulationControlCommandPort;
import com.szzh.loggerserver.util.ProtocolData;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 仿真控制服务。
 */
@Service
public class SimulationControlService implements SimulationControlCommandPort {

    private final SimulationSessionManager sessionManager;

    /**
     * 创建仿真控制服务。
     *
     * @param sessionManager 会话管理器。
     */
    public SimulationControlService(SimulationSessionManager sessionManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
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
            return;
        }
        SimulationSession session = sessionOptional.get();
        if (session.getState() == SimulationSessionState.RUNNING) {
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
            return;
        }
        if (session.getState() == SimulationSessionState.PAUSED) {
            handleResume(instanceId, protocolData);
        }
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
            return;
        }
        SimulationSession session = sessionOptional.get();
        if (session.getState() != SimulationSessionState.RUNNING) {
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
            return;
        }
        SimulationSession session = sessionOptional.get();
        if (session.getState() == SimulationSessionState.RUNNING) {
            return;
        }
        if (session.getState() != SimulationSessionState.PAUSED) {
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
     * 会话状态动作。
     */
    private interface SessionAction {

        /**
         * 执行动作。
         */
        void apply();
    }
}

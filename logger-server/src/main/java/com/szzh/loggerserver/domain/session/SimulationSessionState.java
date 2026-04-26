package com.szzh.loggerserver.domain.session;

/**
 * 仿真实例生命周期状态。
 */
public enum SimulationSessionState {

    PREPARING,

    READY,

    RUNNING,

    PAUSED,

    STOPPED,

    FAILED;

    /**
     * 判断当前状态是否为终态。
     *
     * @return 是否为终态。
     */
    public boolean isTerminal() {
        return this == STOPPED || this == FAILED;
    }
}

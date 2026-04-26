package com.szzh.replayserver.domain.session;

/**
 * 回放会话生命周期状态。
 */
public enum ReplaySessionState {

    PREPARING,

    READY,

    RUNNING,

    PAUSED,

    STOPPED,

    COMPLETED,

    FAILED;

    /**
     * 判断当前状态是否为释放终态。
     *
     * @return 是否为终态。
     */
    public boolean isTerminal() {
        return this == STOPPED || this == FAILED;
    }
}

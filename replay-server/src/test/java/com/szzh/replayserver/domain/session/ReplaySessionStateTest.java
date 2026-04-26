package com.szzh.replayserver.domain.session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 回放会话状态测试。
 */
class ReplaySessionStateTest {

    /**
     * 验证释放终态识别。
     */
    @Test
    void shouldIdentifyTerminalStates() {
        Assertions.assertFalse(ReplaySessionState.PREPARING.isTerminal());
        Assertions.assertFalse(ReplaySessionState.READY.isTerminal());
        Assertions.assertFalse(ReplaySessionState.RUNNING.isTerminal());
        Assertions.assertFalse(ReplaySessionState.PAUSED.isTerminal());
        Assertions.assertFalse(ReplaySessionState.COMPLETED.isTerminal());

        Assertions.assertTrue(ReplaySessionState.STOPPED.isTerminal());
        Assertions.assertTrue(ReplaySessionState.FAILED.isTerminal());
    }
}

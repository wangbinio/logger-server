package com.szzh.loggerserver.domain.clock;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真时钟测试。
 */
class SimulationClockTest {

    /**
     * 验证启动后仿真时间随系统时间推进。
     */
    @Test
    void shouldAdvanceAfterStart() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationClock clock = new SimulationClock(wallClock::get);

        clock.start();
        wallClock.set(1_500L);

        Assertions.assertTrue(clock.isRunning());
        Assertions.assertEquals(1_500L, clock.currentSimTimeMillis());
    }

    /**
     * 验证暂停后仿真时间冻结，继续后恢复推进。
     */
    @Test
    void shouldPauseAndResume() {
        AtomicLong wallClock = new AtomicLong(2_000L);
        SimulationClock clock = new SimulationClock(wallClock::get);
        clock.start();

        wallClock.set(2_300L);
        clock.pause();
        long pausedTime = clock.currentSimTimeMillis();

        wallClock.set(3_000L);
        Assertions.assertEquals(pausedTime, clock.currentSimTimeMillis());

        clock.resume();
        wallClock.set(3_400L);
        Assertions.assertEquals(pausedTime + 400L, clock.currentSimTimeMillis());
    }

    /**
     * 验证倍率调整会影响后续仿真时间推进速度。
     */
    @Test
    void shouldApplySpeedChange() {
        AtomicLong wallClock = new AtomicLong(5_000L);
        SimulationClock clock = new SimulationClock(wallClock::get);
        clock.start();

        wallClock.set(5_200L);
        clock.updateSpeed(2.0D);
        wallClock.set(5_500L);

        Assertions.assertEquals(800L, clock.currentSimTimeMillis() - 5_000L);
        Assertions.assertEquals(2.0D, clock.getSpeed());
    }

    /**
     * 验证非法状态切换会抛出异常。
     */
    @Test
    void shouldRejectInvalidStateTransition() {
        SimulationClock clock = new SimulationClock(() -> 1_000L);

        Assertions.assertThrows(IllegalStateException.class, clock::pause);
        Assertions.assertThrows(IllegalStateException.class, clock::resume);
        Assertions.assertThrows(IllegalStateException.class, () -> clock.updateSpeed(0.0D));

        clock.start();
        Assertions.assertThrows(IllegalStateException.class, clock::start);
    }
}

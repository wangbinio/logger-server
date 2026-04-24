package com.szzh.loggerserver.domain.clock;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 仿真时钟。
 */
public class SimulationClock {

    private final LongSupplier wallClockSupplier;

    private long baseSimTimeMillis;

    private long baseWallClockMillis;

    private double speed = 1.0D;

    private boolean running;

    private boolean initialized;

    /**
     * 使用系统时间创建时钟。
     */
    public SimulationClock() {
        this(System::currentTimeMillis);
    }

    /**
     * 使用指定时间源创建时钟。
     *
     * @param wallClockSupplier 系统时间提供者。
     */
    public SimulationClock(LongSupplier wallClockSupplier) {
        this.wallClockSupplier = Objects.requireNonNull(wallClockSupplier, "wallClockSupplier 不能为空");
    }

    /**
     * 启动仿真时钟。
     */
    public synchronized void start() {
        if (initialized) {
            throw new IllegalStateException("仿真时钟已启动");
        }
        long now = wallClockSupplier.getAsLong();
        this.baseSimTimeMillis = now;
        this.baseWallClockMillis = now;
        this.speed = 1.0D;
        this.running = true;
        this.initialized = true;
    }

    /**
     * 暂停仿真时钟。
     */
    public synchronized void pause() {
        ensureInitialized();
        if (!running) {
            throw new IllegalStateException("仿真时钟未处于运行状态");
        }
        this.baseSimTimeMillis = currentSimTimeMillis();
        this.running = false;
    }

    /**
     * 恢复仿真时钟。
     */
    public synchronized void resume() {
        ensureInitialized();
        if (running) {
            throw new IllegalStateException("仿真时钟已处于运行状态");
        }
        this.baseWallClockMillis = wallClockSupplier.getAsLong();
        this.running = true;
    }

    /**
     * 更新仿真倍率。
     *
     * @param newSpeed 新倍率。
     */
    public synchronized void updateSpeed(double newSpeed) {
        ensureInitialized();
        if (newSpeed <= 0) {
            throw new IllegalArgumentException("仿真倍率必须大于 0");
        }
        this.baseSimTimeMillis = currentSimTimeMillis();
        this.baseWallClockMillis = wallClockSupplier.getAsLong();
        this.speed = newSpeed;
    }

    /**
     * 获取当前仿真时间。
     *
     * @return 当前仿真时间毫秒值。
     */
    public synchronized long currentSimTimeMillis() {
        ensureInitialized();
        if (!running) {
            return baseSimTimeMillis;
        }
        long elapsedMillis = wallClockSupplier.getAsLong() - baseWallClockMillis;
        return baseSimTimeMillis + (long) (elapsedMillis * speed);
    }

    /**
     * 获取当前倍率。
     *
     * @return 当前倍率。
     */
    public synchronized double getSpeed() {
        return speed;
    }

    /**
     * 判断时钟是否处于运行状态。
     *
     * @return 是否运行中。
     */
    public synchronized boolean isRunning() {
        return running;
    }

    /**
     * 获取时钟是否已初始化。
     *
     * @return 是否已初始化。
     */
    public synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * 确保时钟已初始化。
     */
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("仿真时钟尚未启动");
        }
    }
}

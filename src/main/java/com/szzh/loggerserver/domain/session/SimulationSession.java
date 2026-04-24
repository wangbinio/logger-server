package com.szzh.loggerserver.domain.session;

import com.szzh.loggerserver.domain.clock.SimulationClock;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真实例会话。
 */
@Getter
public class SimulationSession {

    private final String instanceId;

    private final SimulationClock simulationClock;

    private final long createdAtMillis;

    @Getter(AccessLevel.NONE)
    private final AtomicLong receivedMessageCount = new AtomicLong();

    @Getter(AccessLevel.NONE)
    private final AtomicLong writtenRecordCount = new AtomicLong();

    @Getter(AccessLevel.NONE)
    private final AtomicLong droppedMessageCount = new AtomicLong();

    private volatile SimulationSessionState state = SimulationSessionState.PREPARING;

    private volatile long lastMessageTimeMillis;

    private volatile String lastErrorMessage;

    private volatile long lastErrorTimeMillis;

    @Setter
    private volatile Object broadcastConsumerHandle;

    @Setter
    private volatile Object situationConsumerHandle;

    /**
     * 创建仿真实例会话。
     *
     * @param instanceId 仿真实例 ID。
     * @param simulationClock 仿真时钟。
     */
    public SimulationSession(String instanceId, SimulationClock simulationClock) {
        this.instanceId = requireInstanceId(instanceId);
        this.simulationClock = Objects.requireNonNull(simulationClock, "simulationClock 不能为空");
        this.createdAtMillis = System.currentTimeMillis();
    }

    /**
     * 更新会话状态。
     *
     * @param nextState 新状态。
     */
    public synchronized void updateState(SimulationSessionState nextState) {
        Objects.requireNonNull(nextState, "nextState 不能为空");
        if (this.state.isTerminal() && this.state != nextState) {
            throw new IllegalStateException("终态会话不允许再次迁移状态");
        }
        this.state = nextState;
    }

    /**
     * 停止当前会话。
     */
    public synchronized void stop() {
        if (this.state == SimulationSessionState.STOPPED) {
            return;
        }
        this.state = SimulationSessionState.STOPPED;
    }

    /**
     * 记录收到一条消息。
     */
    public void markMessageReceived() {
        this.receivedMessageCount.incrementAndGet();
        this.lastMessageTimeMillis = System.currentTimeMillis();
    }

    /**
     * 记录写入一条记录。
     */
    public void markRecordWritten() {
        this.writtenRecordCount.incrementAndGet();
        this.lastMessageTimeMillis = System.currentTimeMillis();
    }

    /**
     * 记录丢弃一条消息。
     */
    public void markMessageDropped() {
        this.droppedMessageCount.incrementAndGet();
        this.lastMessageTimeMillis = System.currentTimeMillis();
    }

    /**
     * 记录异常信息。
     *
     * @param errorMessage 异常信息。
     */
    public void recordFailure(String errorMessage) {
        this.lastErrorMessage = errorMessage;
        this.lastErrorTimeMillis = System.currentTimeMillis();
    }

    /**
     * 获取收到消息计数。
     *
     * @return 收到消息计数。
     */
    public long receivedMessageCount() {
        return receivedMessageCount.get();
    }

    /**
     * 获取写入记录计数。
     *
     * @return 写入记录计数。
     */
    public long writtenRecordCount() {
        return writtenRecordCount.get();
    }

    /**
     * 获取丢弃消息计数。
     *
     * @return 丢弃消息计数。
     */
    public long droppedMessageCount() {
        return droppedMessageCount.get();
    }

    /**
     * 校验实例 ID。
     *
     * @param instanceId 仿真实例 ID。
     * @return 清洗后的实例 ID。
     */
    private String requireInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return instanceId.trim();
    }
}

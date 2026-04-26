package com.szzh.replayserver.mq;

import com.szzh.common.protocol.ProtocolData;

/**
 * 回放任务生命周期命令委派端口。
 */
public interface ReplayLifecycleCommandPort {

    /**
     * 处理创建回放任务命令。
     *
     * @param protocolData 协议数据。
     */
    void handleCreate(ProtocolData protocolData);

    /**
     * 处理停止回放任务命令。
     *
     * @param protocolData 协议数据。
     */
    void handleStop(ProtocolData protocolData);
}

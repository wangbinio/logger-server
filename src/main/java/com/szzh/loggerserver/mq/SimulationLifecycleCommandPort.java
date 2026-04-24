package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.util.ProtocolData;

/**
 * 全局生命周期命令委派端口。
 */
public interface SimulationLifecycleCommandPort {

    /**
     * 处理创建命令。
     *
     * @param protocolData 协议数据。
     */
    void handleCreate(ProtocolData protocolData);

    /**
     * 处理停止命令。
     *
     * @param protocolData 协议数据。
     */
    void handleStop(ProtocolData protocolData);
}

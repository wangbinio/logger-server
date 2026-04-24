package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.util.ProtocolData;

/**
 * 实例控制命令委派端口。
 */
public interface SimulationControlCommandPort {

    /**
     * 处理开始命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handleStart(String instanceId, ProtocolData protocolData);

    /**
     * 处理暂停命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handlePause(String instanceId, ProtocolData protocolData);

    /**
     * 处理继续命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handleResume(String instanceId, ProtocolData protocolData);
}

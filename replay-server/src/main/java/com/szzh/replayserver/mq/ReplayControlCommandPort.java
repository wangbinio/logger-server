package com.szzh.replayserver.mq;

import com.szzh.common.protocol.ProtocolData;

/**
 * 回放实例级控制命令委派端口。
 */
public interface ReplayControlCommandPort {

    /**
     * 处理启动回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handleStart(String instanceId, ProtocolData protocolData);

    /**
     * 处理暂停回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handlePause(String instanceId, ProtocolData protocolData);

    /**
     * 处理继续回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handleResume(String instanceId, ProtocolData protocolData);

    /**
     * 处理倍速回放命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handleRate(String instanceId, ProtocolData protocolData);

    /**
     * 处理时间跳转命令。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handleJump(String instanceId, ProtocolData protocolData);
}

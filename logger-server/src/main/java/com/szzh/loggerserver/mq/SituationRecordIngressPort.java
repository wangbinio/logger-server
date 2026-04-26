package com.szzh.loggerserver.mq;

import com.szzh.common.protocol.ProtocolData;

/**
 * 态势消息入口委派端口。
 */
public interface SituationRecordIngressPort {

    /**
     * 处理态势消息。
     *
     * @param instanceId 实例 ID。
     * @param protocolData 协议数据。
     */
    void handle(String instanceId, ProtocolData protocolData);
}

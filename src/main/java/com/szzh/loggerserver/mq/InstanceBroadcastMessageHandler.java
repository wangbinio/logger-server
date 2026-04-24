package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.util.ProtocolData;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 实例级控制消息处理器。
 */
@Component
public class InstanceBroadcastMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(InstanceBroadcastMessageHandler.class);

    private SimulationControlCommandPort simulationControlCommandPort;

    /**
     * 创建实例级控制消息处理器。
     */
    public InstanceBroadcastMessageHandler() {
    }

    /**
     * 创建实例级控制消息处理器。
     *
     * @param simulationControlCommandPort 控制命令委派端口。
     */
    public InstanceBroadcastMessageHandler(SimulationControlCommandPort simulationControlCommandPort) {
        this.simulationControlCommandPort = simulationControlCommandPort;
    }

    /**
     * 注入控制命令委派端口。
     *
     * @param simulationControlCommandPort 控制命令委派端口。
     */
    @Autowired(required = false)
    public void setSimulationControlCommandPort(SimulationControlCommandPort simulationControlCommandPort) {
        this.simulationControlCommandPort = simulationControlCommandPort;
    }

    /**
     * 处理实例级控制消息。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     */
    public void handle(String instanceId, MessageExt messageExt) {
        ProtocolData protocolData = parse(messageExt);
        if (protocolData == null || !MessageConstants.isInstanceControlMessage(protocolData.getMessageType())) {
            return;
        }
        if (simulationControlCommandPort == null) {
            log.debug("实例控制命令端口尚未接入，instanceId={}", instanceId);
            return;
        }
        switch (protocolData.getMessageCode()) {
            case MessageConstants.INSTANCE_START_MESSAGE_CODE:
                simulationControlCommandPort.handleStart(instanceId, protocolData);
                return;
            case MessageConstants.INSTANCE_PAUSE_MESSAGE_CODE:
                simulationControlCommandPort.handlePause(instanceId, protocolData);
                return;
            case MessageConstants.INSTANCE_RESUME_MESSAGE_CODE:
                simulationControlCommandPort.handleResume(instanceId, protocolData);
                return;
            default:
                log.debug("忽略未知实例控制消息，instanceId={}, messageCode={}",
                        instanceId,
                        protocolData.getMessageCode());
        }
    }

    /**
     * 解析协议消息。
     *
     * @param messageExt RocketMQ 原始消息。
     * @return 协议数据。
     */
    private ProtocolData parse(MessageExt messageExt) {
        Objects.requireNonNull(messageExt, "messageExt 不能为空");
        ProtocolData protocolData = ProtocolMessageUtil.parseData(messageExt.getBody());
        if (protocolData == null) {
            log.warn("实例控制消息协议解析失败，topic={}, msgId={}", messageExt.getTopic(), messageExt.getMsgId());
        }
        return protocolData;
    }
}

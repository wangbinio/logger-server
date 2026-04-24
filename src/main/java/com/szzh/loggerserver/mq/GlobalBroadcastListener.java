package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.support.constant.TopicConstants;
import com.szzh.loggerserver.util.ProtocolData;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 全局广播监听器。
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.GLOBAL_BROADCAST_TOPIC,
        consumerGroup = "${logger-server.rocketmq.global-consumer-group}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING)
@ConditionalOnProperty(
        prefix = "logger-server.rocketmq",
        name = "enable-global-listener",
        havingValue = "true",
        matchIfMissing = true)
public class GlobalBroadcastListener implements RocketMQListener<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(GlobalBroadcastListener.class);

    private SimulationLifecycleCommandPort simulationLifecycleCommandPort;

    /**
     * 创建全局广播监听器。
     *
     */
    public GlobalBroadcastListener() {
    }

    /**
     * 注入生命周期命令委派端口。
     *
     * @param simulationLifecycleCommandPort 生命周期命令委派端口。
     */
    @Autowired(required = false)
    public void setSimulationLifecycleCommandPort(SimulationLifecycleCommandPort simulationLifecycleCommandPort) {
        this.simulationLifecycleCommandPort = simulationLifecycleCommandPort;
    }

    /**
     * 处理全局广播消息。
     *
     * @param messageBody RocketMQ 消息体。
     */
    @Override
    public void onMessage(byte[] messageBody) {
        ProtocolData protocolData = ProtocolMessageUtil.parseData(messageBody);
        if (protocolData == null || !MessageConstants.isGlobalLifecycleMessage(protocolData.getMessageType())) {
            return;
        }
        if (simulationLifecycleCommandPort == null) {
            log.debug("生命周期命令端口尚未接入，messageCode={}", protocolData.getMessageCode());
            return;
        }
        switch (protocolData.getMessageCode()) {
            case MessageConstants.GLOBAL_CREATE_MESSAGE_CODE:
                simulationLifecycleCommandPort.handleCreate(protocolData);
                return;
            case MessageConstants.GLOBAL_STOP_MESSAGE_CODE:
                simulationLifecycleCommandPort.handleStop(protocolData);
                return;
            default:
                log.debug("忽略未知全局消息，messageCode={}", protocolData.getMessageCode());
        }
    }
}

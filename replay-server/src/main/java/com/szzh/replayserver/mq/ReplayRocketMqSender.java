package com.szzh.replayserver.mq;

/**
 * 回放 RocketMQ 发送端口。
 */
public interface ReplayRocketMqSender {

    /**
     * 同步发送消息。
     *
     * @param topic 目标 topic。
     * @param body 消息体。
     */
    void send(String topic, byte[] body);
}

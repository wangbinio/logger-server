package com.szzh.replayserver.config;

import com.szzh.common.exception.BusinessException;
import com.szzh.replayserver.mq.ReplayRocketMqSender;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * 回放 RocketMQ 生产者适配配置。
 */
@Configuration
public class ReplayRocketMqProducerConfig {

    /**
     * 创建回放 RocketMQ 发送适配器。
     *
     * @param rocketMQTemplateProvider RocketMQTemplate 提供者。
     * @return 回放 RocketMQ 发送适配器。
     */
    @Bean
    public ReplayRocketMqSender replayRocketMqSender(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        return new RocketMqTemplateReplaySender(rocketMQTemplateProvider);
    }

    /**
     * 基于 RocketMQTemplate 的发送适配器。
     */
    private static final class RocketMqTemplateReplaySender implements ReplayRocketMqSender {

        private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

        /**
         * 创建发送适配器。
         *
         * @param rocketMQTemplateProvider RocketMQTemplate 提供者。
         */
        private RocketMqTemplateReplaySender(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
            this.rocketMQTemplateProvider =
                    Objects.requireNonNull(rocketMQTemplateProvider, "rocketMQTemplateProvider 不能为空");
        }

        /**
         * 同步发送消息。
         *
         * @param topic 目标 topic。
         * @param body 消息体。
         */
        @Override
        public void send(String topic, byte[] body) {
            RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
            if (rocketMQTemplate == null) {
                throw BusinessException.state("RocketMQTemplate 未配置，无法发布回放态势消息");
            }
            rocketMQTemplate.syncSend(topic, body);
        }
    }
}

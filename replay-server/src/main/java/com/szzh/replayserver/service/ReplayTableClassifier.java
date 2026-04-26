package com.szzh.replayserver.service;

import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 回放态势子表分类器。
 */
@Service
public class ReplayTableClassifier {

    private final Set<MessageKey> eventMessageKeys;

    /**
     * 创建回放态势子表分类器。
     *
     * @param properties 回放服务配置。
     */
    public ReplayTableClassifier(ReplayServerProperties properties) {
        Objects.requireNonNull(properties, "properties 不能为空");
        this.eventMessageKeys = buildEventMessageKeys(properties.getReplay().getEventMessages());
    }

    /**
     * 按事件消息配置分类子表。
     *
     * @param descriptors 待分类子表。
     * @return 已分类子表。
     */
    public List<ReplayTableDescriptor> classify(List<ReplayTableDescriptor> descriptors) {
        List<ReplayTableDescriptor> result = new ArrayList<ReplayTableDescriptor>();
        if (descriptors == null) {
            return result;
        }
        for (ReplayTableDescriptor descriptor : descriptors) {
            ReplayTableType tableType = eventMessageKeys.contains(
                    new MessageKey(descriptor.getMessageType(), descriptor.getMessageCode()))
                    ? ReplayTableType.EVENT
                    : ReplayTableType.PERIODIC;
            result.add(descriptor.withType(tableType));
        }
        return result;
    }

    /**
     * 构建事件消息匹配键集合。
     *
     * @param eventMessages 事件消息配置。
     * @return 消息匹配键集合。
     */
    private Set<MessageKey> buildEventMessageKeys(List<ReplayServerProperties.EventMessage> eventMessages) {
        Set<MessageKey> result = new HashSet<MessageKey>();
        if (eventMessages == null) {
            return result;
        }
        for (ReplayServerProperties.EventMessage eventMessage : eventMessages) {
            if (eventMessage.getMessageCodes() == null) {
                continue;
            }
            for (Integer messageCode : eventMessage.getMessageCodes()) {
                if (messageCode != null) {
                    result.add(new MessageKey(eventMessage.getMessageType(), messageCode));
                }
            }
        }
        return result;
    }

    /**
     * 消息类型和编号组合键。
     */
    private static final class MessageKey {

        private final int messageType;

        private final int messageCode;

        /**
         * 创建消息组合键。
         *
         * @param messageType 消息类型。
         * @param messageCode 消息编号。
         */
        private MessageKey(int messageType, int messageCode) {
            this.messageType = messageType;
            this.messageCode = messageCode;
        }

        /**
         * 比较消息组合键。
         *
         * @param object 待比较对象。
         * @return 是否相同。
         */
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof MessageKey)) {
                return false;
            }
            MessageKey messageKey = (MessageKey) object;
            return messageType == messageKey.messageType && messageCode == messageKey.messageCode;
        }

        /**
         * 获取消息组合键哈希值。
         *
         * @return 哈希值。
         */
        @Override
        public int hashCode() {
            return Objects.hash(messageType, messageCode);
        }
    }
}

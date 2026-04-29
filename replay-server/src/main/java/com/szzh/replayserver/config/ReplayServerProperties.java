package com.szzh.replayserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * replay-server 自定义配置属性。
 */
@Data
@ConfigurationProperties(prefix = "replay-server")
public class ReplayServerProperties {

    private final Tdengine tdengine = new Tdengine();

    private final RocketMq rocketmq = new RocketMq();

    private final Protocol protocol = new Protocol();

    private final Replay replay = new Replay();

    /**
     * TDengine 连接配置。
     */
    @Data
    public static class Tdengine {

        private String jdbcUrl;

        private String username;

        private String password;

        private String driverClassName = "com.taosdata.jdbc.ws.WebSocketDriver";

        private int maximumPoolSize = 4;

        private long connectionTimeoutMs = 30000L;
    }

    /**
     * RocketMQ 业务配置。
     */
    @Data
    public static class RocketMq {

        private String globalConsumerGroup = "replay-global-consumer";

        private String instanceConsumerGroupPrefix = "replay-instance";

        private String producerGroup = "replay-producer";

        private boolean enableGlobalListener = true;
    }

    /**
     * 协议处理配置。
     */
    @Data
    public static class Protocol {

        private int maxPayloadSize = 102400;

        private final Messages messages = new Messages();
    }

    /**
     * 协议消息分类配置。
     */
    @Data
    public static class Messages {

        private final Global global = new Global();

        private final Control control = new Control();
    }

    /**
     * 全局回放任务管理消息配置。
     */
    @Data
    public static class Global {

        private int messageType = 0;

        private int createMessageCode = 2;

        private int stopMessageCode = 3;
    }

    /**
     * 实例级回放控制消息配置。
     */
    @Data
    public static class Control {

        private int messageType = 1200;

        private int startMessageCode = 1;

        private int pauseMessageCode = 2;

        private int resumeMessageCode = 3;

        private int rateMessageCode = 4;

        private int jumpMessageCode = 5;

        private int metadataMessageCode = 9;
    }

    /**
     * 回放业务配置。
     */
    @Data
    public static class Replay {

        private final Query query = new Query();

        private final Scheduler scheduler = new Scheduler();

        private final Publish publish = new Publish();

        private final List<EventMessage> eventMessages = new ArrayList<EventMessage>();
    }

    /**
     * 回放查询配置。
     */
    @Data
    public static class Query {

        private int pageSize = 1000;

        private int stopMessageType = 0;

        private int stopMessageCode = 1;
    }

    /**
     * 回放调度配置。
     */
    @Data
    public static class Scheduler {

        private long tickMillis = 50L;
    }

    /**
     * 回放发布配置。
     */
    @Data
    public static class Publish {

        private int batchSize = 500;

        private int retryTimes = 3;
    }

    /**
     * 事件类消息配置。
     */
    @Data
    public static class EventMessage {

        private int messageType;

        private List<Integer> messageCodes = new ArrayList<Integer>();
    }
}

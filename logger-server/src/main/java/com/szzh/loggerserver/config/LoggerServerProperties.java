package com.szzh.loggerserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * logger-server 自定义配置属性。
 */
@Data
@ConfigurationProperties(prefix = "logger-server")
public class LoggerServerProperties {

    private final Tdengine tdengine = new Tdengine();

    private final Protocol protocol = new Protocol();

    private final Session session = new Session();

    private final Write write = new Write();

    private final RocketMq rocketmq = new RocketMq();

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

        private final Instance instance = new Instance();
    }

    /**
     * 全局生命周期消息配置。
     */
    @Data
    public static class Global {

        private int messageType = 0;

        private int createMessageCode = 0;

        private int stopMessageCode = 1;
    }

    /**
     * 实例控制消息配置。
     */
    @Data
    public static class Instance {

        private int messageType = 1100;

        private int startMessageCode = 1;

        private int pauseMessageCode = 5;

        private int resumeMessageCode = 6;
    }

    /**
     * 会话管理配置。
     */
    @Data
    public static class Session {

        private long cleanupDelaySeconds = 30L;
    }

    /**
     * 写入控制配置。
     */
    @Data
    public static class Write {

        private int retryTimes = 3;

        private int batchSize = 500;
    }

    /**
     * RocketMQ 业务配置。
     */
    @Data
    public static class RocketMq {

        private String globalConsumerGroup = "logger-global-consumer";

        private String instanceConsumerGroupPrefix = "logger-instance";

        private boolean enableGlobalListener = true;
    }
}

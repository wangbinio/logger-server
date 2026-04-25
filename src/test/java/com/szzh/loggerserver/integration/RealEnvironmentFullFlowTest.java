package com.szzh.loggerserver.integration;

import com.szzh.loggerserver.LoggerServerApplication;
import com.szzh.loggerserver.config.LoggerServerProperties;
import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.mq.GlobalBroadcastListener;
import com.szzh.loggerserver.mq.SimulationLifecycleCommandPort;
import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.support.constant.TdengineConstants;
import com.szzh.loggerserver.support.constant.TopicConstants;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * 真实环境完整流程测试。
 */
@SpringBootTest(
        classes = LoggerServerApplication.class,
        properties = "logger-server.rocketmq.enable-global-listener=false")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "logger.real-env.test", matches = "true")
class RealEnvironmentFullFlowTest {

    private static final Logger log = LoggerFactory.getLogger(RealEnvironmentFullFlowTest.class);

    private static final int TEST_DURATION_SECONDS = 60;

    private static final int PAUSE_AT_SECOND = 20;

    private static final int RESUME_AT_SECOND = 25;

    private static final int EXPECTED_TABLE_COUNT = 25;

    private static final int EXPECTED_RECORDS_PER_TABLE = 11;

    private static final int EXPECTED_TOTAL_RECORDS = EXPECTED_TABLE_COUNT * EXPECTED_RECORDS_PER_TABLE;

    @Autowired
    private RocketMQProperties rocketMQProperties;

    @Autowired
    private SimulationSessionManager simulationSessionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SimulationLifecycleCommandPort simulationLifecycleCommandPort;

    @Autowired
    private LoggerServerProperties loggerServerProperties;

    @Autowired
    private MessageConstants messageConstants;

    /**
     * 验证真实环境下可完成创建、启动、暂停、继续、停止以及 TDengine 落库校验。
     *
     * @throws Exception 测试过程中发生异常。
     */
    @Test
    void shouldCompleteFullFlowAgainstRealEnvironment() throws Exception {
        String instanceId = "real-it-" + System.currentTimeMillis();
        ensureTdengineDatabaseExists();
        DefaultMQPushConsumer globalConsumer = createGlobalConsumer();
        DefaultMQProducer producer = createProducer();
        try {
            TimeUnit.SECONDS.sleep(2);
            ensureTopicExists(producer, TopicConstants.buildInstanceBroadcastTopic(instanceId));
            ensureTopicExists(producer, TopicConstants.buildInstanceSituationTopic(instanceId));
            TimeUnit.SECONDS.sleep(2);

            sendGlobalCreate(producer, instanceId);
            waitUntil("会话进入 READY 状态", 30, new BooleanSupplier() {
                /**
                 * 判断条件是否成立。
                 *
                 * @return 条件判断结果。
                 */
                @Override
                public boolean getAsBoolean() {
                    Optional<SimulationSession> sessionOptional = simulationSessionManager.getSession(instanceId);
                    return sessionOptional.isPresent()
                            && sessionOptional.get().getState() == SimulationSessionState.READY;
                }
            });

            sendInstanceControl(producer, instanceId, messageConstants.getInstanceStartMessageCode());
            waitUntil("会话进入 RUNNING 状态", 30, new BooleanSupplier() {
                /**
                 * 判断条件是否成立。
                 *
                 * @return 条件判断结果。
                 */
                @Override
                public boolean getAsBoolean() {
                    return simulationSessionManager.getSession(instanceId)
                            .map(session -> session.getState() == SimulationSessionState.RUNNING)
                            .orElse(false);
                }
            });

            long nextTickMillis = System.currentTimeMillis();
            for (int second = 0; second < TEST_DURATION_SECONDS; second++) {
                if (second == PAUSE_AT_SECOND) {
                    sendInstanceControl(producer, instanceId, messageConstants.getInstancePauseMessageCode());
                    waitUntil("会话进入 PAUSED 状态", 30, new BooleanSupplier() {
                        /**
                         * 判断条件是否成立。
                         *
                         * @return 条件判断结果。
                         */
                        @Override
                        public boolean getAsBoolean() {
                            return simulationSessionManager.getSession(instanceId)
                                    .map(session -> session.getState() == SimulationSessionState.PAUSED)
                                    .orElse(false);
                        }
                    });
                }
                if (second == RESUME_AT_SECOND) {
                    sendInstanceControl(producer, instanceId, messageConstants.getInstanceResumeMessageCode());
                    waitUntil("会话恢复 RUNNING 状态", 30, new BooleanSupplier() {
                        /**
                         * 判断条件是否成立。
                         *
                         * @return 条件判断结果。
                         */
                        @Override
                        public boolean getAsBoolean() {
                            return simulationSessionManager.getSession(instanceId)
                                    .map(session -> session.getState() == SimulationSessionState.RUNNING)
                                    .orElse(false);
                        }
                    });
                }

                int messageCode = second % 5 + 1;
                // 每一秒为 5 个 sender 顺序发送一轮消息，整体满足“每个 sender 以 1s 周期循环发送 messageCode 1-5”。
                for (int senderId = 1; senderId <= 5; senderId++) {
                    sendSituationMessage(producer, instanceId, senderId, messageCode);
                }

                nextTickMillis += 1000L;
                sleepUntil(nextTickMillis);
            }

            sendGlobalStop(producer, instanceId);
            waitUntil("会话被停止并移除", 30, new BooleanSupplier() {
                /**
                 * 判断条件是否成立。
                 *
                 * @return 条件判断结果。
                 */
                @Override
                public boolean getAsBoolean() {
                    return !simulationSessionManager.getSession(instanceId).isPresent();
                }
            });

            waitUntil("TDengine 落库结果达到预期", 30, new BooleanSupplier() {
                /**
                 * 判断 TDengine 中的记录数是否全部符合预期。
                 *
                 * @return 是否达到预期。
                 */
                @Override
                public boolean getAsBoolean() {
                    return hasExpectedTdengineCounts(instanceId);
                }
            });

            Map<String, Long> tableRowCountMap = queryTableRowCount(instanceId);
            Assertions.assertEquals(EXPECTED_TABLE_COUNT, tableRowCountMap.size(), "子表数量不符合预期");
            for (Map.Entry<String, Long> entry : tableRowCountMap.entrySet()) {
                Assertions.assertEquals(Long.valueOf(EXPECTED_RECORDS_PER_TABLE),
                        entry.getValue(),
                        "子表记录数不符合预期: " + entry.getKey());
            }

            long totalCount = queryCount(TdengineConstants.buildStableName(instanceId));
            Assertions.assertEquals(EXPECTED_TOTAL_RECORDS, totalCount, "实例超表总记录数不符合预期");
            log.info("真实环境完整测试完成，instanceId={}, tableRowCountMap={}, totalCount={}",
                    instanceId,
                    tableRowCountMap,
                    totalCount);
        } finally {
            producer.shutdown();
            shutdownQuietly(globalConsumer);
        }
    }

    /**
     * 创建 RocketMQ 测试生产者。
     *
     * @return 已启动的测试生产者。
     * @throws MQClientException RocketMQ 异常。
     */
    private DefaultMQProducer createProducer() throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer("logger-real-env-test-producer-" + System.nanoTime());
        producer.setNamesrvAddr(rocketMQProperties.getNameServer());
        producer.setInstanceName("logger-real-env-test-instance-" + System.nanoTime());
        producer.start();
        return producer;
    }

    /**
     * 确保真实环境测试依赖的 TDengine 数据库已经存在。
     *
     * @throws Exception 数据库初始化异常。
     */
    private void ensureTdengineDatabaseExists() throws Exception {
        LoggerServerProperties.Tdengine tdengine = loggerServerProperties.getTdengine();
        String jdbcUrl = tdengine.getJdbcUrl();
        String databaseName = extractDatabaseName(jdbcUrl);
        String adminJdbcUrl = buildAdminJdbcUrl(jdbcUrl);
        Class.forName(tdengine.getDriverClassName());
        try (Connection connection = DriverManager.getConnection(
                adminJdbcUrl,
                tdengine.getUsername(),
                tdengine.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + databaseName);
        }
    }

    /**
     * 创建真实环境测试使用的全局广播消费者。
     *
     * @return 已启动的全局广播消费者。
     * @throws MQClientException RocketMQ 异常。
     */
    private DefaultMQPushConsumer createGlobalConsumer() throws MQClientException {
        final GlobalBroadcastListener listener = new GlobalBroadcastListener(messageConstants);
        listener.setSimulationLifecycleCommandPort(simulationLifecycleCommandPort);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(
                "logger-real-env-global-consumer-" + System.nanoTime());
        consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(1);
        consumer.setInstanceName("logger-real-env-global-instance-" + System.nanoTime());
        applyAccessChannel(consumer);
        consumer.subscribe(TopicConstants.GLOBAL_BROADCAST_TOPIC, "*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            /**
             * 消费全局广播消息并委派给全局监听器。
             *
             * @param messages 消息列表。
             * @param context 消费上下文。
             * @return 消费结果。
             */
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,
                                                            ConsumeConcurrentlyContext context) {
                for (MessageExt message : messages) {
                    listener.onMessage(message);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        return consumer;
    }

    /**
     * 从 TDengine JDBC URL 中提取数据库名称。
     *
     * @param jdbcUrl TDengine JDBC URL。
     * @return 数据库名称。
     */
    private String extractDatabaseName(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            throw new IllegalStateException("logger-server.tdengine.jdbc-url 未配置");
        }
        int queryIndex = jdbcUrl.indexOf('?');
        String mainPart = queryIndex >= 0 ? jdbcUrl.substring(0, queryIndex) : jdbcUrl;
        int pathStartIndex = mainPart.indexOf('/', mainPart.indexOf("://") + 3);
        if (pathStartIndex < 0 || pathStartIndex == mainPart.length() - 1) {
            throw new IllegalStateException("TDengine JDBC URL 中未包含数据库名: " + jdbcUrl);
        }
        return mainPart.substring(pathStartIndex + 1);
    }

    /**
     * 从 TDengine JDBC URL 中移除数据库路径，构造管理连接地址。
     *
     * @param jdbcUrl TDengine JDBC URL。
     * @return 不绑定具体数据库的管理连接地址。
     */
    private String buildAdminJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            throw new IllegalStateException("logger-server.tdengine.jdbc-url 未配置");
        }
        int queryIndex = jdbcUrl.indexOf('?');
        String queryPart = queryIndex >= 0 ? jdbcUrl.substring(queryIndex) : "";
        String mainPart = queryIndex >= 0 ? jdbcUrl.substring(0, queryIndex) : jdbcUrl;
        int pathStartIndex = mainPart.indexOf('/', mainPart.indexOf("://") + 3);
        if (pathStartIndex < 0) {
            return jdbcUrl;
        }
        return mainPart.substring(0, pathStartIndex) + queryPart;
    }

    /**
     * 确保实例级测试 topic 已存在。
     *
     * @param producer RocketMQ 生产者。
     * @param topic 主题名。
     * @throws MQClientException RocketMQ 异常。
     */
    private void ensureTopicExists(DefaultMQProducer producer, String topic) throws MQClientException {
        producer.createTopic("TBW102", topic, 4, null);
    }

    /**
     * 发送全局创建消息。
     *
     * @param producer RocketMQ 生产者。
     * @param instanceId 实例 ID。
     * @throws Exception 消息发送异常。
     */
    private void sendGlobalCreate(DefaultMQProducer producer, String instanceId) throws Exception {
        producer.send(new Message(
                TopicConstants.GLOBAL_BROADCAST_TOPIC,
                ProtocolMessageUtil.buildData(
                        0,
                        (short) messageConstants.getGlobalMessageType(),
                        messageConstants.getGlobalCreateMessageCode(),
                        buildInstancePayload(instanceId))));
    }

    /**
     * 发送全局停止消息。
     *
     * @param producer RocketMQ 生产者。
     * @param instanceId 实例 ID。
     * @throws Exception 消息发送异常。
     */
    private void sendGlobalStop(DefaultMQProducer producer, String instanceId) throws Exception {
        producer.send(new Message(
                TopicConstants.GLOBAL_BROADCAST_TOPIC,
                ProtocolMessageUtil.buildData(
                        0,
                        (short) messageConstants.getGlobalMessageType(),
                        messageConstants.getGlobalStopMessageCode(),
                        buildInstancePayload(instanceId))));
    }

    /**
     * 发送实例控制消息。
     *
     * @param producer RocketMQ 生产者。
     * @param instanceId 实例 ID。
     * @param messageCode 控制消息编号。
     * @throws Exception 消息发送异常。
     */
    private void sendInstanceControl(DefaultMQProducer producer,
                                     String instanceId,
                                     int messageCode) throws Exception {
        producer.send(new Message(
                TopicConstants.buildInstanceBroadcastTopic(instanceId),
                ProtocolMessageUtil.buildData(
                        0,
                        (short) messageConstants.getInstanceControlMessageType(),
                        messageCode,
                        new byte[0])));
    }

    /**
     * 发送态势消息。
     *
     * @param producer RocketMQ 生产者。
     * @param instanceId 实例 ID。
     * @param senderId 发送方 ID。
     * @param messageCode 消息编号。
     * @throws Exception 消息发送异常。
     */
    private void sendSituationMessage(DefaultMQProducer producer,
                                      String instanceId,
                                      int senderId,
                                      int messageCode) throws Exception {
        int messageType = senderId;
        producer.send(new Message(
                TopicConstants.buildInstanceSituationTopic(instanceId),
                ProtocolMessageUtil.buildData(
                        senderId,
                        (short) messageType,
                        messageCode,
                        buildSituationPayload(senderId, messageType, messageCode))));
    }

    /**
     * 构造实例控制 JSON 载荷。
     *
     * @param instanceId 实例 ID。
     * @return JSON 字节数组。
     */
    private byte[] buildInstancePayload(String instanceId) {
        return ("{\"instanceId\":\"" + instanceId + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 构造态势消息 JSON 载荷。
     *
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @return JSON 字节数组。
     */
    private byte[] buildSituationPayload(int senderId, int messageType, int messageCode) {
        String payload = String.format(
                "{\"senderId\":%d,\"messageType\":%d,\"messageCode\":%d}",
                senderId,
                messageType,
                messageCode);
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 判断当前实例在 TDengine 中的统计结果是否达到预期。
     *
     * @param instanceId 实例 ID。
     * @return 是否达到预期。
     */
    private boolean hasExpectedTdengineCounts(String instanceId) {
        Map<String, Long> tableRowCountMap = queryTableRowCountSafely(instanceId);
        if (tableRowCountMap.size() != EXPECTED_TABLE_COUNT) {
            return false;
        }
        for (Long rowCount : tableRowCountMap.values()) {
            if (rowCount == null || rowCount.longValue() != EXPECTED_RECORDS_PER_TABLE) {
                return false;
            }
        }
        return queryCountSafely(TdengineConstants.buildStableName(instanceId)) == EXPECTED_TOTAL_RECORDS;
    }

    /**
     * 查询当前实例下各子表记录数。
     *
     * @param instanceId 实例 ID。
     * @return 子表与记录数映射。
     */
    private Map<String, Long> queryTableRowCount(String instanceId) {
        Map<String, Long> tableRowCountMap = new LinkedHashMap<String, Long>();
        for (int senderId = 1; senderId <= 5; senderId++) {
            for (int messageCode = 1; messageCode <= 5; messageCode++) {
                String tableName = TdengineConstants.buildSubTableName(instanceId, senderId, messageCode, senderId);
                tableRowCountMap.put(tableName, queryCount(tableName));
            }
        }
        return tableRowCountMap;
    }

    /**
     * 安全查询当前实例下各子表记录数；查询失败时返回空映射用于重试。
     *
     * @param instanceId 实例 ID。
     * @return 子表与记录数映射。
     */
    private Map<String, Long> queryTableRowCountSafely(String instanceId) {
        try {
            return queryTableRowCount(instanceId);
        } catch (RuntimeException exception) {
            log.info("TDengine 子表记录数尚未稳定，instanceId={}, reason={}", instanceId, exception.getMessage());
            return new LinkedHashMap<String, Long>();
        }
    }

    /**
     * 查询指定表的记录数。
     *
     * @param tableName 表名。
     * @return 记录数。
     */
    private long queryCount(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 安全查询指定表记录数；查询失败时返回 -1 用于等待重试。
     *
     * @param tableName 表名。
     * @return 记录数，失败时返回 -1。
     */
    private long queryCountSafely(String tableName) {
        try {
            return queryCount(tableName);
        } catch (RuntimeException exception) {
            log.info("TDengine 表记录数尚未稳定，tableName={}, reason={}", tableName, exception.getMessage());
            return -1L;
        }
    }

    /**
     * 应用 RocketMQ AccessChannel 配置。
     *
     * @param consumer RocketMQ 消费者。
     */
    private void applyAccessChannel(DefaultMQPushConsumer consumer) {
        if (!StringUtils.hasText(rocketMQProperties.getAccessChannel())) {
            return;
        }
        consumer.setAccessChannel(AccessChannel.valueOf(
                rocketMQProperties.getAccessChannel().trim().toUpperCase(Locale.ENGLISH)));
    }

    /**
     * 在给定超时时间内等待条件成立。
     *
     * @param description 等待描述。
     * @param timeoutSeconds 超时秒数。
     * @param condition 等待条件。
     * @throws InterruptedException 线程中断异常。
     */
    private void waitUntil(String description,
                           long timeoutSeconds,
                           BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() <= deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
        Assertions.fail(description + "超时");
    }

    /**
     * 休眠到下一个目标时刻。
     *
     * @param targetMillis 目标时间戳。
     * @throws InterruptedException 线程中断异常。
     */
    private void sleepUntil(long targetMillis) throws InterruptedException {
        long sleepMillis = targetMillis - System.currentTimeMillis();
        if (sleepMillis > 0) {
            TimeUnit.MILLISECONDS.sleep(sleepMillis);
        }
    }

    /**
     * 静默关闭全局广播消费者。
     *
     * @param consumer RocketMQ 消费者。
     */
    private void shutdownQuietly(DefaultMQPushConsumer consumer) {
        if (consumer == null) {
            return;
        }
        try {
            consumer.shutdown();
        } catch (RuntimeException exception) {
            log.warn("关闭真实环境全局广播消费者失败", exception);
        }
    }
}

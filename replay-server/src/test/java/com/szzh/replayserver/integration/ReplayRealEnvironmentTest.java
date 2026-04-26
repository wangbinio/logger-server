package com.szzh.replayserver.integration;

import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.tdengine.TdengineNaming;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.ReplayServerApplication;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.service.ReplayLifecycleService;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * 回放真实环境集成测试。
 */
@SpringBootTest(
        classes = ReplayServerApplication.class,
        properties = "replay-server.rocketmq.enable-global-listener=false")
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "replay.real-env.test", matches = "true")
class ReplayRealEnvironmentTest {

    private static final long START_SIM_TIME = 1_000L;

    private static final long END_SIM_TIME = 4_000L;

    private static final int REPLAY_FRAME_COUNT = 3;

    private static final String CREATE_STABLE_SQL_TEMPLATE =
            "CREATE STABLE IF NOT EXISTS %s (ts TIMESTAMP, simtime BIGINT, rawdata VARBINARY(8192)) "
                    + "TAGS (sender_id INT, msgtype INT, msgcode INT)";

    private static final String CREATE_TIME_CONTROL_TABLE_SQL_TEMPLATE =
            "CREATE TABLE IF NOT EXISTS %s (ts TIMESTAMP, simtime BIGINT, rate DOUBLE, "
                    + "sender_id INT, msgtype INT, msgcode INT)";

    @Autowired
    private RocketMQProperties rocketMQProperties;

    @Autowired
    private ReplayServerProperties replayServerProperties;

    @Autowired
    private ReplayMessageConstants messageConstants;

    @Autowired
    private ReplayLifecycleService lifecycleService;

    @Autowired
    private ReplaySessionManager sessionManager;

    @Autowired
    private ReplayMetrics replayMetrics;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证真实 TDengine 数据可通过真实 RocketMQ 控制 topic 回放到态势 topic。
     *
     * @throws Exception 测试过程中发生异常。
     */
    @Test
    void shouldReplayRecordedTdengineDataAgainstRealRocketMqAndTdengine() throws Exception {
        String instanceId = "replay-real-it-" + System.currentTimeMillis();
        String broadcastTopic = TopicConstants.buildInstanceBroadcastTopic(instanceId);
        String situationTopic = TopicConstants.buildInstanceSituationTopic(instanceId);
        List<ProtocolData> receivedFrames = Collections.synchronizedList(new ArrayList<ProtocolData>());
        ensureTdengineDatabaseExists();
        prepareRecordedReplayData(instanceId);
        DefaultMQProducer producer = null;
        DefaultMQPushConsumer situationConsumer = null;
        try {
            producer = createProducer();
            ensureTopicExists(producer, broadcastTopic);
            ensureTopicExists(producer, situationTopic);
            situationConsumer = createSituationConsumer(instanceId, receivedFrames);
            TimeUnit.SECONDS.sleep(2);

            lifecycleService.createReplay(instanceId);
            waitUntil("回放会话进入 READY 状态", 30, new BooleanSupplier() {
                /**
                 * 判断会话是否进入 READY。
                 *
                 * @return 是否进入 READY。
                 */
                @Override
                public boolean getAsBoolean() {
                    return sessionManager.getSession(instanceId)
                            .map(session -> session.getState() == ReplaySessionState.READY)
                            .orElse(false);
                }
            });

            sendControl(producer, instanceId, messageConstants.getInstanceStartMessageCode(), "{}");
            waitUntil("回放会话进入 RUNNING 状态", 30, new BooleanSupplier() {
                /**
                 * 判断会话是否进入 RUNNING。
                 *
                 * @return 是否进入 RUNNING。
                 */
                @Override
                public boolean getAsBoolean() {
                    return sessionManager.getSession(instanceId)
                            .map(session -> session.getState() == ReplaySessionState.RUNNING)
                            .orElse(false);
                }
            });

            sendControl(producer, instanceId, messageConstants.getInstancePauseMessageCode(), "{}");
            waitUntil("回放会话进入 PAUSED 状态", 30, new BooleanSupplier() {
                /**
                 * 判断会话是否进入 PAUSED。
                 *
                 * @return 是否进入 PAUSED。
                 */
                @Override
                public boolean getAsBoolean() {
                    return sessionManager.getSession(instanceId)
                            .map(session -> session.getState() == ReplaySessionState.PAUSED)
                            .orElse(false);
                }
            });

            sendControl(producer, instanceId, messageConstants.getInstanceRateMessageCode(), "{\"rate\":2.0}");
            waitUntil("回放倍率更新为 2.0", 30, new BooleanSupplier() {
                /**
                 * 判断回放倍率是否更新。
                 *
                 * @return 是否已更新。
                 */
                @Override
                public boolean getAsBoolean() {
                    return sessionManager.getSession(instanceId)
                            .map(session -> Math.abs(session.getRate() - 2.0D) < 0.0001D)
                            .orElse(false);
                }
            });

            sendControl(producer, instanceId, messageConstants.getInstanceResumeMessageCode(), "{}");
            waitUntil("真实回放态势消息发布完成", 30, new BooleanSupplier() {
                /**
                 * 判断态势消息是否发布完成。
                 *
                 * @return 是否发布完成。
                 */
                @Override
                public boolean getAsBoolean() {
                    return receivedFrames.size() >= REPLAY_FRAME_COUNT;
                }
            });

            waitUntil("回放水位推进到结束时间", 30, new BooleanSupplier() {
                /**
                 * 判断回放水位是否到达结束时间。
                 *
                 * @return 是否到达结束时间。
                 */
                @Override
                public boolean getAsBoolean() {
                    return sessionManager.getSession(instanceId)
                            .map(session -> session.getLastDispatchedSimTime() >= END_SIM_TIME
                                    || session.getState() == ReplaySessionState.COMPLETED)
                            .orElse(false);
                }
            });

            Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
            Assertions.assertTrue(sessionOptional.isPresent(), "回放会话不存在");
            ReplaySession session = sessionOptional.get();
            Assertions.assertTrue(session.getLastDispatchedSimTime() >= END_SIM_TIME
                            || session.getState() == ReplaySessionState.COMPLETED,
                    "回放水位或完成状态不符合预期");
            Assertions.assertEquals(REPLAY_FRAME_COUNT, receivedFrames.size(), "真实回放消息数量不符合预期");
            Assertions.assertTrue(replayMetrics.publishedSuccessCount() >= REPLAY_FRAME_COUNT,
                    "发布成功指标未达到预期");
        } finally {
            lifecycleService.stopReplay(instanceId);
            shutdownQuietly(situationConsumer);
            if (producer != null) {
                producer.shutdown();
            }
        }
    }

    /**
     * 准备真实 TDengine 回放数据。
     *
     * @param instanceId 实例 ID。
     */
    private void prepareRecordedReplayData(String instanceId) {
        String stableName = TdengineNaming.buildStableName(instanceId);
        String timeControlTableName = TdengineNaming.buildTimeControlTableName(instanceId);
        jdbcTemplate.execute(String.format(CREATE_STABLE_SQL_TEMPLATE, stableName));
        jdbcTemplate.execute(String.format(CREATE_TIME_CONTROL_TABLE_SQL_TEMPLATE, timeControlTableName));

        // 写入控制时间点数据，供回放创建阶段解析起止时间。
        jdbcTemplate.update("INSERT INTO " + timeControlTableName + " VALUES (NOW, ?, ?, ?, ?, ?)",
                START_SIM_TIME, 1.0D, 0, messageConstants.getInstanceControlMessageType(),
                messageConstants.getInstanceStartMessageCode());
        jdbcTemplate.update("INSERT INTO " + timeControlTableName + " VALUES (NOW, ?, ?, ?, ?, ?)",
                END_SIM_TIME, 0.0D, 0, replayServerProperties.getReplay().getQuery().getStopMessageType(),
                replayServerProperties.getReplay().getQuery().getStopMessageCode());

        // 写入真实态势子表数据，供 ReplayFrameRepository 通过 TDengine 查询后回放。
        insertReplayFrame(instanceId, 1001, 1, 7, 1_500L);
        insertReplayFrame(instanceId, 1002, 2, 8, 2_000L);
        insertReplayFrame(instanceId, 1003, 3, 9, 3_000L);
    }

    /**
     * 写入单条回放态势帧。
     *
     * @param instanceId 实例 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param senderId 发送方 ID。
     * @param simTime 仿真时间。
     */
    private void insertReplayFrame(String instanceId,
                                   int messageType,
                                   int messageCode,
                                   int senderId,
                                   long simTime) {
        String stableName = TdengineNaming.buildStableName(instanceId);
        String tableName = TdengineNaming.buildSubTableName(instanceId, messageType, messageCode, senderId);
        byte[] rawData = ("{\"instanceId\":\"" + instanceId + "\",\"simTime\":" + simTime + "}")
                .getBytes(StandardCharsets.UTF_8);
        jdbcTemplate.update("INSERT INTO " + tableName + " USING " + stableName
                        + " TAGS (?, ?, ?) VALUES (NOW, ?, ?)",
                senderId, messageType, messageCode, simTime, rawData);
    }

    /**
     * 创建 RocketMQ 测试生产者。
     *
     * @return 已启动的测试生产者。
     * @throws MQClientException RocketMQ 异常。
     */
    private DefaultMQProducer createProducer() throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer("replay-real-env-test-producer-" + System.nanoTime());
        producer.setNamesrvAddr(rocketMQProperties.getNameServer());
        producer.setInstanceName("replay-real-env-test-producer-instance-" + System.nanoTime());
        producer.start();
        return producer;
    }

    /**
     * 创建态势 topic 测试消费者。
     *
     * @param instanceId 实例 ID。
     * @param receivedFrames 已接收帧列表。
     * @return 已启动的测试消费者。
     * @throws MQClientException RocketMQ 异常。
     */
    private DefaultMQPushConsumer createSituationConsumer(final String instanceId,
                                                         final List<ProtocolData> receivedFrames)
            throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(
                "replay-real-env-situation-consumer-" + System.nanoTime());
        consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(1);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setInstanceName("replay-real-env-situation-instance-" + System.nanoTime());
        applyAccessChannel(consumer);
        consumer.subscribe(TopicConstants.buildInstanceSituationTopic(instanceId), "*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            /**
             * 消费回放态势消息。
             *
             * @param messages 消息列表。
             * @param context 消费上下文。
             * @return 消费结果。
             */
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,
                                                            ConsumeConcurrentlyContext context) {
                for (MessageExt message : messages) {
                    receivedFrames.add(ProtocolMessageUtil.parseData(message.getBody()));
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        return consumer;
    }

    /**
     * 发送实例控制消息。
     *
     * @param producer RocketMQ 生产者。
     * @param instanceId 实例 ID。
     * @param messageCode 消息编号。
     * @param rawJson JSON 载荷。
     * @throws Exception 消息发送异常。
     */
    private void sendControl(DefaultMQProducer producer,
                             String instanceId,
                             int messageCode,
                             String rawJson) throws Exception {
        producer.send(new Message(
                TopicConstants.buildInstanceBroadcastTopic(instanceId),
                ProtocolMessageUtil.buildData(
                        0,
                        (short) messageConstants.getInstanceControlMessageType(),
                        messageCode,
                        rawJson.getBytes(StandardCharsets.UTF_8))));
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
     * 确保真实环境测试依赖的 TDengine 数据库已经存在。
     *
     * @throws Exception 数据库初始化异常。
     */
    private void ensureTdengineDatabaseExists() throws Exception {
        ReplayServerProperties.Tdengine tdengine = replayServerProperties.getTdengine();
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
     * 从 TDengine JDBC URL 中提取数据库名称。
     *
     * @param jdbcUrl TDengine JDBC URL。
     * @return 数据库名称。
     */
    private String extractDatabaseName(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            throw new IllegalStateException("replay-server.tdengine.jdbc-url 未配置");
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
            throw new IllegalStateException("replay-server.tdengine.jdbc-url 未配置");
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
            TimeUnit.MILLISECONDS.sleep(200L);
        }
        Assertions.fail(description + "超时");
    }

    /**
     * 静默关闭 RocketMQ 消费者。
     *
     * @param consumer RocketMQ 消费者。
     */
    private void shutdownQuietly(DefaultMQPushConsumer consumer) {
        if (consumer == null) {
            return;
        }
        try {
            consumer.shutdown();
        } catch (RuntimeException ignored) {
            // 测试清理阶段只做尽力关闭，避免覆盖原始断言结果。
        }
    }
}

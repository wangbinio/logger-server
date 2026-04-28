package com.szzh.replayserver.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.szzh.common.json.JsonUtil;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.tdengine.TdengineNaming;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.ReplayServerApplication;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.mq.ReplayGlobalBroadcastListener;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回放真实环境集成测试。
 */
@SpringBootTest(classes = ReplayServerApplication.class)
@ActiveProfiles({"dev", "real"})
@EnabledIfSystemProperty(named = "replay.real-env.test", matches = "true")
@AutoConfigureMockMvc
class ReplayRealEnvironmentTest {

    private static final long START_SIM_TIME = 1_000L;

    private static final long END_SIM_TIME = 4_000L;

    private static final long JUMP_TARGET_SIM_TIME = 2_600L;

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
    private ObjectProvider<ReplayGlobalBroadcastListener> globalBroadcastListenerProvider;

    @Autowired
    private ReplaySessionManager sessionManager;

    @Autowired
    private MockMvc mockMvc;

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
        String situationTopic = TopicConstants.buildInstanceSituationTopic(instanceId);
        List<ProtocolData> receivedFrames = Collections.synchronizedList(new ArrayList<ProtocolData>());
        assertRealProfileConfiguration();
        ensureTdengineDatabaseExists();
        List<ExpectedFrame> expectedFrames = prepareRecordedReplayData(instanceId);
        DefaultMQProducer producer = null;
        DefaultMQPushConsumer situationConsumer = null;
        try {
            producer = createProducer();
            ensureTopicExists(producer, TopicConstants.GLOBAL_BROADCAST_TOPIC);
            ensureTopicExists(producer, situationTopic);
            situationConsumer = createSituationConsumer(instanceId, receivedFrames);
            TimeUnit.SECONDS.sleep(2);

            sendGlobalLifecycle(producer, messageConstants.getGlobalCreateMessageCode(), instanceId);
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

            postControl(instanceId, "jump", "{\"time\":" + JUMP_TARGET_SIM_TIME + "}");
            waitUntil("真实回放态势消息发布完成", 30, new BooleanSupplier() {
                /**
                 * 判断态势消息是否发布完成。
                 *
                 * @return 是否发布完成。
                 */
                @Override
                public boolean getAsBoolean() {
                    return receivedFrames.size() >= expectedFrames.size();
                }
            });
            waitUntil("回放水位推进到跳转目标时间", 30, new BooleanSupplier() {
                /**
                 * 判断回放水位是否到达跳转目标时间。
                 *
                 * @return 是否到达跳转目标时间。
                 */
                @Override
                public boolean getAsBoolean() {
                    return sessionManager.getSession(instanceId)
                            .map(session -> session.getLastDispatchedSimTime() == JUMP_TARGET_SIM_TIME)
                            .orElse(false);
                }
            });

            Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
            Assertions.assertTrue(sessionOptional.isPresent(), "回放会话不存在");
            ReplaySession session = sessionOptional.get();
            Assertions.assertEquals(JUMP_TARGET_SIM_TIME, session.getLastDispatchedSimTime(),
                    "跳转后的回放水位不符合预期");
            assertPublishedFrames(expectedFrames, receivedFrames);
            Assertions.assertTrue(replayMetrics.publishedSuccessCount() >= expectedFrames.size(),
                    "发布成功指标未达到预期");

            sendGlobalLifecycle(producer, messageConstants.getGlobalStopMessageCode(), instanceId);
            waitUntil("全局 stop 释放回放会话", 30, new BooleanSupplier() {
                /**
                 * 判断回放会话是否已释放。
                 *
                 * @return 是否已释放。
                 */
                @Override
                public boolean getAsBoolean() {
                    return !sessionManager.getSession(instanceId).isPresent();
                }
            });
        } finally {
            stopByGlobalMessageQuietly(producer, instanceId);
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
     * @return 期望发布帧。
     */
    private List<ExpectedFrame> prepareRecordedReplayData(String instanceId) {
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
        ExpectedFrame eventAt1500 = insertReplayFrame(instanceId, 1001, 1, 7, 1_500L);
        ExpectedFrame eventAt1800 = insertReplayFrame(instanceId, 1002, 8, 4, 1_800L);
        ExpectedFrame eventAt2500 = insertReplayFrame(instanceId, 1001, 2, 6, 2_500L);
        ExpectedFrame periodicAt2600 = insertReplayFrame(instanceId, 1003, 3, 9, 2_600L);
        insertReplayFrame(instanceId, 1003, 3, 9, 3_000L);
        return Arrays.asList(eventAt1500, eventAt1800, eventAt2500, periodicAt2600);
    }

    /**
     * 写入单条回放态势帧。
     *
     * @param instanceId 实例 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param senderId 发送方 ID。
     * @param simTime 仿真时间。
     * @return 写入帧对应的期望值。
     */
    private ExpectedFrame insertReplayFrame(String instanceId,
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
        return new ExpectedFrame(senderId, messageType, messageCode, simTime, rawData);
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
     * 发送全局生命周期消息。
     *
     * @param producer RocketMQ 生产者。
     * @param messageCode 消息编号。
     * @param instanceId 实例 ID。
     * @throws Exception 消息发送异常。
     */
    private void sendGlobalLifecycle(DefaultMQProducer producer, int messageCode, String instanceId) throws Exception {
        byte[] rawData = ("{\"instanceId\":\"" + instanceId + "\"}").getBytes(StandardCharsets.UTF_8);
        producer.send(new Message(
                TopicConstants.GLOBAL_BROADCAST_TOPIC,
                ProtocolMessageUtil.buildData(
                        0,
                        (short) messageConstants.getGlobalMessageType(),
                        messageCode,
                        rawData)));
    }

    /**
     * 通过 HTTP 接口发送回放控制请求。
     *
     * @param instanceId 实例 ID。
     * @param action 控制动作。
     * @param rawJson JSON 请求体。
     * @throws Exception MockMvc 调用异常。
     */
    private void postControl(String instanceId, String action, String rawJson) throws Exception {
        mockMvc.perform(post("/api/replay/instances/" + instanceId + "/" + action)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk());
    }

    /**
     * 断言 real profile 与全局监听器真实生效。
     */
    private void assertRealProfileConfiguration() {
        Assertions.assertTrue(replayServerProperties.getRocketmq().isEnableGlobalListener(),
                "真实环境 profile 必须启用全局监听器");
        Assertions.assertNotNull(globalBroadcastListenerProvider.getIfAvailable(),
                "真实环境测试必须加载 ReplayGlobalBroadcastListener");
    }

    /**
     * 校验真实发布帧内容、协议字段、顺序和重复。
     *
     * @param expectedFrames 期望帧。
     * @param receivedFrames 实际帧。
     */
    private void assertPublishedFrames(List<ExpectedFrame> expectedFrames, List<ProtocolData> receivedFrames) {
        Assertions.assertEquals(expectedFrames.size(), receivedFrames.size(), "真实回放消息数量不符合预期");
        Set<String> uniqueKeys = new HashSet<String>();
        for (int index = 0; index < expectedFrames.size(); index++) {
            ExpectedFrame expectedFrame = expectedFrames.get(index);
            ProtocolData actualFrame = receivedFrames.get(index);
            Assertions.assertTrue(uniqueKeys.add(toFrameKey(actualFrame)), "真实回放消息存在重复帧");
            Assertions.assertEquals(expectedFrame.getSenderId(), actualFrame.getSenderId(), "senderId 映射不符合预期");
            Assertions.assertEquals(expectedFrame.getMessageType(), actualFrame.getMessageType(), "messageType 映射不符合预期");
            Assertions.assertEquals(expectedFrame.getMessageCode(), actualFrame.getMessageCode(), "messageCode 映射不符合预期");
            Assertions.assertArrayEquals(expectedFrame.getRawData(), actualFrame.getRawData(), "rawData 不符合预期");
            Assertions.assertEquals(expectedFrame.getSimTime(), readSimTime(actualFrame), "rawData.simTime 不符合预期");
        }
    }

    /**
     * 读取协议数据中的仿真时间。
     *
     * @param protocolData 协议数据。
     * @return 仿真时间。
     */
    private long readSimTime(ProtocolData protocolData) {
        JsonNode jsonNode = JsonUtil.readTree(protocolData.getRawData());
        JsonNode simTimeNode = jsonNode.get("simTime");
        if (simTimeNode == null || !simTimeNode.isNumber()) {
            throw new IllegalArgumentException("rawData 缺少数值字段 simTime");
        }
        return simTimeNode.asLong();
    }

    /**
     * 生成真实回放帧去重键。
     *
     * @param protocolData 协议数据。
     * @return 去重键。
     */
    private String toFrameKey(ProtocolData protocolData) {
        return protocolData.getSenderId()
                + "|"
                + protocolData.getMessageType()
                + "|"
                + protocolData.getMessageCode()
                + "|"
                + new String(protocolData.getRawData(), StandardCharsets.UTF_8);
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
     * 通过全局 stop 消息尽力清理真实回放会话。
     *
     * @param producer RocketMQ 生产者。
     * @param instanceId 实例 ID。
     */
    private void stopByGlobalMessageQuietly(DefaultMQProducer producer, String instanceId) {
        if (producer == null || !sessionManager.getSession(instanceId).isPresent()) {
            return;
        }
        try {
            sendGlobalLifecycle(producer, messageConstants.getGlobalStopMessageCode(), instanceId);
            waitUntil("清理回放会话", 10, new BooleanSupplier() {
                /**
                 * 判断回放会话是否已清理。
                 *
                 * @return 是否已清理。
                 */
                @Override
                public boolean getAsBoolean() {
                    return !sessionManager.getSession(instanceId).isPresent();
                }
            });
        } catch (Exception ignored) {
            // 清理阶段不覆盖原始失败原因。
        }
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

    /**
     * 真实回放期望帧。
     */
    private static final class ExpectedFrame {

        private final int senderId;

        private final int messageType;

        private final int messageCode;

        private final long simTime;

        private final byte[] rawData;

        /**
         * 创建真实回放期望帧。
         *
         * @param senderId 发送方 ID。
         * @param messageType 消息类型。
         * @param messageCode 消息编号。
         * @param simTime 仿真时间。
         * @param rawData 原始数据。
         */
        private ExpectedFrame(int senderId, int messageType, int messageCode, long simTime, byte[] rawData) {
            this.senderId = senderId;
            this.messageType = messageType;
            this.messageCode = messageCode;
            this.simTime = simTime;
            this.rawData = rawData;
        }

        /**
         * 获取发送方 ID。
         *
         * @return 发送方 ID。
         */
        private int getSenderId() {
            return senderId;
        }

        /**
         * 获取消息类型。
         *
         * @return 消息类型。
         */
        private int getMessageType() {
            return messageType;
        }

        /**
         * 获取消息编号。
         *
         * @return 消息编号。
         */
        private int getMessageCode() {
            return messageCode;
        }

        /**
         * 获取仿真时间。
         *
         * @return 仿真时间。
         */
        private long getSimTime() {
            return simTime;
        }

        /**
         * 获取原始数据。
         *
         * @return 原始数据。
         */
        private byte[] getRawData() {
            return rawData;
        }
    }
}

# 任务：修复全局监听器消息类型

## 背景

当前应用在生产式启动路径下会自动注册 `broadcast-global` 注解监听器，但 `GlobalBroadcastListener` 声明为 `RocketMQListener<byte[]>`。结合 `rocketmq-spring-boot 2.2.3` 的 `DefaultRocketMQListenerContainer` 实现可确认：注解监听模式下，仅当监听器泛型为 `MessageExt` 或 RocketMQ 原始 `Message` 时，容器才会直接传递原始消息对象；否则会先把消息体解码为 `String` 再做类型转换。于是当前实现会在容器回调时触发 `String -> byte[]` 的错误强转，导致生产环境 `broadcast-global` 消费持续失败并反复重试。

## 计划

- [x] 复核 `GlobalBroadcastListener`、相关测试与 `rocketmq-spring` 容器行为，确认最小生产修复方案。
- [x] 先补充测试，覆盖“全局监听器必须接收 `MessageExt` 原始消息并从 `body` 解析协议”的约束。
- [x] 修改实现并验证自动启动路径下的消息处理不再依赖错误的 `byte[]` 泛型转换。
- [x] 执行相关单测与回归测试，记录验证结果。

## Review

- 已通过本地查阅 `rocketmq-spring-boot 2.2.3` 源码确认根因：`DefaultRocketMQListenerContainer#doConvertMessage` 仅在监听器泛型为 `MessageExt` 或 RocketMQ 原始 `Message` 时直接透传原始消息；其他类型会先将 `body` 解码为 `String`，再走 `MessageConverter`。因此 `GlobalBroadcastListener implements RocketMQListener<byte[]>` 在注解监听模式下会稳定触发 `String -> byte[]` 的强转失败。
- 已新增测试 `src/test/java/com/szzh/loggerserver/mq/GlobalBroadcastListenerTest.java`，锁定两条约束：
  - 全局监听器必须声明为 `RocketMQListener<MessageExt>`。
  - 监听器必须从原始 `MessageExt.body` 中解析协议并正常委派创建命令。
- 已修改 `src/main/java/com/szzh/loggerserver/mq/GlobalBroadcastListener.java`：
  - 将监听器泛型从 `byte[]` 调整为 `MessageExt`。
  - 将 `onMessage` 入参改为原始 `MessageExt`，统一从 `getBody()` 解析协议。
  - 补充实现注释，明确这里必须接收 `MessageExt` 的原因，防止后续误改回 `byte[]`。
- 已同步修正测试侧调用路径 `src/test/java/com/szzh/loggerserver/integration/SimulationFlowIntegrationTest.java` 与 `src/test/java/com/szzh/loggerserver/integration/RealEnvironmentFullFlowTest.java`，保证测试和生产路径一致，都以 `MessageExt` 驱动全局监听器。
- 已完成验证：
  - `mvn -q -Dtest=GlobalBroadcastListenerTest,SimulationFlowIntegrationTest test` 通过。
  - `mvn -q test` 在 Java 8 环境下通过。
- 当前结论：
  - 这次修复解决的是代码层的生产问题，不依赖“清空 RocketMQ”之类环境手段。
  - 旧的重试消息即使还在，也不会再因为 `String cannot be cast to [B` 这类类型错误而持续失败；后续若出现消费异常，应按真实业务或协议内容继续排查，而不是回到监听器签名问题。

# 任务：排查真实环境 RocketMQ 监听失败

## 计划

- [x] 对比 `TopicSubscriptionManagerTest` 与 `RealEnvironmentFullFlowTest` 的消费者创建路径
- [x] 在当前代码与当前真实环境下重跑两个 RocketMQ 相关测试
- [x] 分析 `broadcast-global` 监听失败的直接根因与环境约束
- [x] 给出最小修复建议或后续排查指令

## 执行记录

- [x] 重新执行 `mvn -q -Dtest=TopicSubscriptionManagerTest#shouldConsumeMessagesWithRealRocketMq test`，结果仍为跳过，跳过点在 `createTopic("TBW102", 动态 topic)` 失败，而不是实例级消费者启动失败。
- [x] 重新执行 `mvn -q -Dlogger.real-env.test=true -Dtest=RealEnvironmentFullFlowTest test`，结果仍失败在 Spring 上下文启动阶段，由 `GlobalBroadcastListener` 自动注册的 `DefaultRocketMQListenerContainer` 启动时报 `RemotingConnectException: connect to null failed`。
- [x] 使用 Java 8 编写临时探针，直接创建 `DefaultMQPushConsumer` 订阅 `broadcast-global`，分别验证“工厂风格参数”“Spring 风格参数”“与真实测试完全相同的 group + instanceName”，三者均可正常启动。
- [x] 在临时探针中先初始化 TDengine `HikariDataSource`，再启动同样的 RocketMQ 消费者，仍可正常启动，说明问题不是单纯由 TDengine 驱动或数据源初始化直接触发。
- [x] 使用最小 Spring Boot 上下文验证 `@RocketMQMessageListener(topic = "broadcast-global")`，监听容器可以成功启动，说明 RocketMQ 环境和 `rocketmq-spring` 基本链路本身并非完全不可用。

## Review

- [x] 当前能确认的结论不是“RocketMQ 不能用”，而是“`RealEnvironmentFullFlowTest` 启动完整项目上下文时，自动全局监听器这条集成路径不稳定/失败”。
- [x] 该问题不在测试方法体内，而在 `@SpringBootTest` 启动 `LoggerServerApplication` 期间；因此即使手工提前创建了 `broadcast-global`，也无法保证该测试通过。
- [x] 若目标是让真实环境全流程测试稳定运行，最小改法应是对 `RealEnvironmentFullFlowTest` 关闭自动全局监听器，并在测试中显式创建全局广播消费者，绕开当前失败的自动容器启动路径。

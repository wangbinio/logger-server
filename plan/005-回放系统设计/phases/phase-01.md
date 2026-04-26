# Phase 01 replay-server 配置与消息入口

## 1. 阶段目标

建立 `replay-server` 的基础运行骨架，包括配置模型、消息码常量、全局回放任务监听、实例级回放控制处理器和只订阅 `broadcast-{instanceId}` 的动态订阅管理器。

本阶段不读取 TDengine 数据，也不发布态势数据，只验证回放服务能正确接入和过滤控制消息。

## 2. 实现思路

回放服务和记录服务都监听 `broadcast-global`，因此必须用 `messageType` 做严格隔离。回放服务全局消息类型固定配置为 `1`，实例级回放控制消息类型固定配置为 `1200`。动态订阅管理器只负责订阅 `broadcast-{instanceId}`，禁止复用记录系统中同时订阅控制与态势 topic 的管理器。

消息入口层只做解析、过滤、异常吞吐和委托端口调用，业务状态机留到后续阶段。

## 3. 需要新增的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/.../config/ReplayServerProperties.java` | 回放服务配置绑定。 |
| `replay-server/src/main/java/.../config/ReplayRocketMqConsumerFactory.java` | 回放动态消费者工厂。 |
| `replay-server/src/main/java/.../support/constant/ReplayMessageConstants.java` | 回放消息码配置快照。 |
| `replay-server/src/main/java/.../mq/ReplayGlobalBroadcastListener.java` | 固定监听 `broadcast-global`。 |
| `replay-server/src/main/java/.../mq/ReplayInstanceBroadcastMessageHandler.java` | 处理 `broadcast-{instanceId}` 控制消息。 |
| `replay-server/src/main/java/.../mq/ReplayTopicSubscriptionManager.java` | 只订阅和取消订阅 `broadcast-{instanceId}`。 |
| `replay-server/src/main/java/.../mq/ReplayLifecycleCommandPort.java` | 回放任务生命周期委派端口。 |
| `replay-server/src/main/java/.../mq/ReplayControlCommandPort.java` | 回放控制委派端口。 |
| `replay-server/src/main/resources/application.yml` | 回放服务基础配置。 |
| `replay-server/src/main/resources/application-dev.yml` | 开发环境 RocketMQ 与 TDengine 配置。 |
| `replay-server/src/test/java/.../support/constant/ReplayMessageConstantsTest.java` | 消息码配置测试。 |
| `replay-server/src/test/java/.../mq/ReplayGlobalBroadcastListenerTest.java` | 全局监听器测试。 |
| `replay-server/src/test/java/.../mq/ReplayInstanceBroadcastMessageHandlerTest.java` | 实例控制处理器测试。 |
| `replay-server/src/test/java/.../mq/ReplayTopicSubscriptionManagerTest.java` | 动态订阅测试。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 01-01 | 新增 `ReplayServerProperties` 测试 | 先验证默认消息类型、控制消息码、消费者组前缀和发布配置能正确绑定。 | Phase 00 |
| 01-02 | 实现 `ReplayServerProperties` | 建立 `tdengine`、`rocketmq`、`protocol.messages`、`replay` 配置域。 | 01-01 |
| 01-03 | 新增 `ReplayMessageConstantsTest` | 验证全局消息类型为 `1`，实例控制消息类型为 `1200`，并能识别各消息码。 | 01-02 |
| 01-04 | 实现 `ReplayMessageConstants` | 从配置中生成不可变消息码快照，提供判断方法和 getter。 | 01-03 |
| 01-05 | 新增全局监听器测试 | 覆盖协议解析失败、非回放消息忽略、创建和停止消息委托、未知消息忽略。 | 01-04 |
| 01-06 | 实现 `ReplayGlobalBroadcastListener` | 固定监听 `broadcast-global`，只处理回放全局消息。 | 01-05 |
| 01-07 | 新增实例控制处理器测试 | 覆盖开始、暂停、继续、倍速、时间跳转、元信息消息忽略和未知消息忽略。 | 01-04 |
| 01-08 | 实现 `ReplayInstanceBroadcastMessageHandler` | 解析 `broadcast-{instanceId}` 消息并委托控制端口。 | 01-07 |
| 01-09 | 新增动态订阅管理器测试 | 验证只创建 `broadcast-{instanceId}` 消费者，不创建 `situation-{instanceId}` 消费者。 | 01-08 |
| 01-10 | 实现 `ReplayRocketMqConsumerFactory` | 创建实例级控制消费者，消费线程默认单实例串行。 | 01-09 |
| 01-11 | 实现 `ReplayTopicSubscriptionManager` | 管理 `instanceId -> broadcastConsumer`，支持幂等订阅、取消和容器销毁。 | 01-10 |
| 01-12 | 运行阶段测试 | 运行 `replay-server` 模块中配置与 MQ 入口相关测试。 | 01-11 |

## 5. 验证要求

- 非回放全局消息不会触发回放任务创建或停止。
- `messageCode=9` 的元信息通知不会被回放控制处理器当成控制命令。
- 动态订阅管理器只订阅 `broadcast-{instanceId}`。
- 消息解析失败只记录指标或日志，不导致监听线程退出。

## 6. Review

### 6.1 实际改动

- 新增 `ReplayServerProperties`，覆盖 `tdengine`、`rocketmq`、`protocol.messages`、`replay.query`、`replay.scheduler`、`replay.publish` 和事件消息配置域。
- 新增 `ReplayMessageConstants`，从配置生成回放全局消息与实例控制消息快照，并提供消息类型与消息码判断方法。
- 新增 `ReplayLifecycleCommandPort` 和 `ReplayControlCommandPort`，为后续生命周期服务和控制服务预留委派边界。
- 新增 `ReplayGlobalBroadcastListener`，固定监听 `broadcast-global`，只处理 `messageType=1` 的回放创建与停止消息。
- 新增 `ReplayInstanceBroadcastMessageHandler`，只处理 `messageType=1200` 的启动、暂停、继续、倍速和时间跳转消息，并忽略 `messageCode=9` 元信息通知。
- 新增 `ReplayRocketMqConsumerFactory` 与 `ReplayTopicSubscriptionManager`，动态订阅只创建 `broadcast-{instanceId}` 消费者，不创建或管理 `situation-{instanceId}` 消费者。
- `ReplayServerApplication` 显式启用 `ReplayServerProperties` 与 `RocketMQProperties` 配置绑定，保证上下文测试在排除 RocketMQ 自动配置时仍能加载回放侧 Bean。

### 6.2 验证结果

- TDD 首轮 `mvn -pl replay-server -am test` 已按预期失败，失败原因为回放配置、消息常量和 MQ 入口生产类尚未实现。
- 实现后 `mvn -pl replay-server -am test` 通过，`common` 12 个测试通过，`replay-server` 22 个测试通过。
- 完整 `mvn test` 通过，完整 reactor `logger-platform`、`common`、`logger-server`、`replay-server` 全部成功；其中 `logger-server` 59 个测试运行，0 失败，0 错误，1 个真实环境测试按设计跳过；`replay-server` 22 个测试全部通过。

### 6.3 遗留风险

- 本阶段只完成消息入口与动态订阅管理，不读取 TDengine、不创建 `ReplaySession`、不发布态势数据。
- `ReplayLifecycleCommandPort` 与 `ReplayControlCommandPort` 当前只有委派边界，实际状态机将在后续 Phase 03、Phase 04、Phase 05 中落地。
- 动态订阅管理器已按 Phase 01 要求单实例串行消费，但真实 RocketMQ 环境下的 topic 创建、路由和消息投递仍留到 Phase 06 联调验收。

## 7. 当前无需澄清的问题

本阶段没有阻塞性疑问。

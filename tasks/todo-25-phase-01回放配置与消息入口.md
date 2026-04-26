# Phase 01 回放配置与消息入口执行

## 背景

按照 `plan/005-回放系统设计/development-steps.md` 与 `phases/phase-01.md` 执行 Phase 01，建立 `replay-server` 的配置模型、消息常量、全局回放任务监听器、实例级回放控制处理器和只订阅 `broadcast-{instanceId}` 的动态订阅管理器。

## 执行计划

- [x] 检查 Phase 01 设计、当前 `replay-server` 骨架和记录侧同类实现。
- [x] 将 Phase 01 状态标记为进行中。
- [x] 先补 `ReplayServerProperties` 配置绑定测试并确认失败。
- [x] 先补 `ReplayMessageConstants` 消息隔离测试并确认失败。
- [x] 先补全局监听器与实例级控制处理器测试并确认失败。
- [x] 先补动态订阅管理器和消费者工厂测试并确认失败。
- [x] 实现 `ReplayServerProperties`、`ReplayMessageConstants`、委派端口和 MQ 入口。
- [x] 实现 `ReplayRocketMqConsumerFactory` 和 `ReplayTopicSubscriptionManager`，确保不订阅 `situation-{instanceId}`。
- [x] 运行 `mvn -pl replay-server -am test` 验证。
- [x] 回写 Phase 01 文档、开发步骤索引和本任务单 Review。

## Review

已完成 Phase 01。`replay-server` 现在具备配置绑定、回放消息常量快照、`broadcast-global` 全局回放任务监听、`broadcast-{instanceId}` 实例控制处理器，以及只订阅实例控制 topic 的动态订阅管理器。

TDD 记录：

- 首轮 `mvn -pl replay-server -am test` 按预期失败，失败原因为 `ReplayServerProperties`、`ReplayMessageConstants` 和 MQ 入口类尚未实现。
- 实现后 `mvn -pl replay-server -am test` 通过，`common` 12 个测试通过，`replay-server` 22 个测试通过。
- 完整 `mvn test` 通过，`common`、`logger-server`、`replay-server` 全部成功；`logger-server` 59 个测试中 1 个真实环境测试按设计跳过，`replay-server` 22 个测试全部通过。

遗留风险：

- 本阶段未实现 TDengine 查询、回放会话状态机、态势发布和真实 RocketMQ 联调。
- `ReplayLifecycleCommandPort` 与 `ReplayControlCommandPort` 目前只是委派端口，后续阶段需要接入实际服务实现。

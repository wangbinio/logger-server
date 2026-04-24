# 任务：Phase 03 执行

## 背景

根据 `plan\002-详细开发步骤\phases\phase-03.md`，本阶段需要完成 RocketMQ 固定监听、实例级动态订阅、消息处理器以及订阅管理测试。用户额外要求测试应使用 `application.yml` 与 `application-local.yml` 中配置的真实 RocketMQ 环境。

## 执行项

- [x] 梳理 Phase 03 与 Phase 04 的职责边界，避免提前耦合完整业务编排。
- [x] 先编写订阅管理测试，覆盖重复订阅、重复取消、会话不存在、异常回收。
- [x] 增加基于真实 RocketMQ 配置的动态订阅集成测试。
- [x] 实现 `RocketMqConsumerFactory`。
- [x] 实现 `TopicSubscriptionManager`。
- [x] 实现 `GlobalBroadcastListener`。
- [x] 实现 `InstanceBroadcastMessageHandler`。
- [x] 实现 `SituationMessageHandler`。
- [x] 使用 Java 8 运行 Maven 测试。
- [x] 回填 review。

## 验收标准

- 实例级 consumer 可按 `instanceId` 动态创建、启动、停止和回收。
- 全局监听与实例消息处理器已具备协议解析和委派能力。
- 订阅管理具备幂等保护和异常清理能力。
- 测试在 Java 8 下通过，且至少包含一项真实 RocketMQ 环境验证。

## Review

- 已新增 `RocketMqConsumerFactory`，统一封装实例级控制与态势消费者的创建、公共参数和命名规则。
- 已新增 `TopicSubscriptionManager`，实现 `instanceId -> consumer` 订阅映射、幂等订阅/取消、异常回收以及会话句柄绑定。
- 已新增 `GlobalBroadcastListener`、`InstanceBroadcastMessageHandler`、`SituationMessageHandler`，完成协议解析、消息分类和委派边界，为 Phase 04 服务层接入预留端口。
- 已新增 `TopicSubscriptionManagerTest`，覆盖重复订阅、重复取消、会话不存在、启动异常清理，并读取 `application.yml + application-local.yml` 执行真实 RocketMQ 环境探测。
- 已增加 `logger-server.rocketmq.enable-global-listener` 开关，用于在测试上下文中关闭固定全局监听，避免环境问题污染基础上下文测试。
- 已在 Java 8 环境下执行 `mvn -q test` 并通过。
  - `JAVA_HOME=C:\Users\summer\.jdks\corretto-1.8.0_482`
  - 命令：`mvn -q test`
- 完成测试。

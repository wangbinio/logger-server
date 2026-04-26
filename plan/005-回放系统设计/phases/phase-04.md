# Phase 04 发布与连续调度

## 1. 阶段目标

实现回放数据向 RocketMQ 的发布能力，以及运行状态下按回放时钟持续调度数据窗口的能力。

本阶段完成后，回放系统应能在不处理跳转的前提下，从 `simulationStartTime` 连续发布到 `simulationEndTime`。

## 2. 实现思路

`ReplaySituationPublisher` 负责将 TDengine 中读出的 `ReplayFrame` 重新组装为平台协议包，并发送到 `situation-{instanceId}`。`ReplayScheduler` 负责按固定 tick 计算当前回放时间，查询 `(lastDispatchedSimTime, currentReplayTime]` 数据，发布成功后推进水位。

水位推进必须严格放在发布成功之后，发布失败时会话进入失败态，不能伪装成功。

## 3. 需要新增的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/.../config/ReplayRocketMqProducerConfig.java` | RocketMQ 生产者配置。 |
| `replay-server/src/main/java/.../mq/ReplaySituationPublisher.java` | 回放态势发布器。 |
| `replay-server/src/main/java/.../service/ReplayScheduler.java` | 连续回放调度器。 |
| `replay-server/src/main/java/.../service/ReplayFrameMergeService.java` | 多表分页结果归并服务。 |
| `replay-server/src/test/java/.../mq/ReplaySituationPublisherTest.java` | 发布器测试。 |
| `replay-server/src/test/java/.../service/ReplayFrameMergeServiceTest.java` | 帧归并测试。 |
| `replay-server/src/test/java/.../service/ReplaySchedulerTest.java` | 调度器测试。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 04-01 | 新增发布器测试 | 验证 topic 为 `situation-{instanceId}`，协议包使用 frame 的 senderId、messageType、messageCode 和 rawData 重组。 | Phase 02, Phase 03 |
| 04-02 | 实现 `ReplayRocketMqProducerConfig` | 配置 `RocketMQTemplate` 或封装生产者，使用独立 producer group。 | 04-01 |
| 04-03 | 实现 `ReplaySituationPublisher` | 支持发送重试，重试失败抛出业务异常。 | 04-02 |
| 04-04 | 新增帧归并测试 | 多张表数据按 `simtime` 升序，同一毫秒内按 messageType、messageCode、senderId、tableName 稳定排序。 | 03-08, 02-12 |
| 04-05 | 实现 `ReplayFrameMergeService` | 用小顶堆或排序列表归并多表分页结果，保持稳定顺序。 | 04-04 |
| 04-06 | 新增调度器测试 | 覆盖 RUNNING 下查询并发布、PAUSED 不查询、发布失败不推进水位、到达结束时间进入 COMPLETED。 | 04-05 |
| 04-07 | 实现 `ReplayScheduler` 基础结构 | 使用 `ScheduledExecutorService` 或等价调度器，按配置 tick 执行。 | 04-06 |
| 04-08 | 实现连续窗口调度 | 计算 `(lastDispatchedSimTime, currentReplayTime]`，查询、归并、发布并推进水位。 | 04-07 |
| 04-09 | 实现自然结束逻辑 | 到达结束时间并发布完最后窗口后，将会话状态迁移为 `COMPLETED`，保留控制订阅。 | 04-08 |
| 04-10 | 运行阶段测试 | 运行发布器、帧归并和调度器测试。 | 04-09 |

## 5. 验证要求

- 发布失败时不推进 `lastDispatchedSimTime`。
- 暂停状态下调度器不查询新窗口。
- 同一 `simtime` 的消息排序稳定。
- 到达结束时间后进入 `COMPLETED`，但不自动取消 `broadcast-{instanceId}` 订阅。

## 6. 当前无需澄清的问题

本阶段没有阻塞性疑问。

## 7. Review

- 已完成 04-01 至 04-10：新增发布器、帧归并和调度器测试，并实现 `ReplayRocketMqProducerConfig`、`ReplayRocketMqSender`、`ReplaySituationPublisher`、`ReplayFrameMergeService`、`ReplayScheduler`。
- 发布链路会将 `ReplayFrame` 的 senderId、messageType、messageCode 和 rawData 重新组装为平台协议包，发送到 `situation-{instanceId}`，并按配置重试；重试耗尽后抛出业务异常。
- 连续调度按 `(lastDispatchedSimTime, currentReplayTime]` 查询事件表和周期表，归并后发布；只有全部发布成功才推进水位，发布或查询失败时会话进入失败态且水位不推进。
- 自然结束逻辑已实现：窗口到达 `simulationEndTime` 后会话迁移到 `COMPLETED`，不自动取消 `broadcast-{instanceId}` 控制订阅。
- 已补充 `rocketmq.producer.group=replay-producer`，满足 RocketMQ 生产者自动配置前置条件；常规测试上下文仍通过排除自动配置保持外部依赖隔离。
- 验证通过：`mvn -pl replay-server -am test`，`common` 12 个测试通过，`replay-server` 65 个测试通过。
- 验证通过：`mvn test`，`common` 12 个测试通过，`logger-server` 59 个测试中 2 个按现有开关跳过，`replay-server` 65 个测试通过。
- 遗留边界：本阶段不处理时间跳转后的补偿发布，向前跳转、向后跳转和周期快照补发留给 Phase 05。

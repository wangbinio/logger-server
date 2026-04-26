# Phase 04 发布与连续调度执行

## 背景

按照 `plan/005-回放系统设计/development-steps.md` 与 `phases/phase-04.md` 执行 Phase 04，实现 `replay-server` 的回放态势发布、帧归并和连续窗口调度。

本阶段只处理连续回放窗口，不实现时间跳转补偿逻辑；时间跳转留给 Phase 05。

## 执行计划

- [x] 检查 Phase 04 设计、总设计连续回放章节和现有 Phase 02/03 接口。
- [x] 将 Phase 04 状态标记为进行中。
- [x] 先补 `ReplaySituationPublisherTest` 并确认失败。
- [x] 先补 `ReplayFrameMergeServiceTest` 并确认失败。
- [x] 先补 `ReplaySchedulerTest` 并确认失败。
- [x] 实现 RocketMQ 发布适配、态势发布器、帧归并服务和连续调度器。
- [x] 运行 `mvn -pl replay-server -am test` 验证阶段测试。
- [x] 运行完整 `mvn test` 回归。
- [x] 回写 Phase 04 文档、开发步骤索引和本任务单 Review。

## Review

- 代码完成：新增 `ReplayRocketMqSender` 发送端口、`ReplayRocketMqProducerConfig` 生产者适配、`ReplaySituationPublisher` 发布器、`ReplayFrameMergeService` 归并服务和 `ReplayScheduler` 连续窗口调度器。
- 测试完成：新增 `ReplaySituationPublisherTest`、`ReplayFrameMergeServiceTest`、`ReplaySchedulerTest`，覆盖协议包重组、topic、重试失败、稳定排序、暂停跳过、发布失败不推进水位和自然完成。
- 配置完成：`replay-server/src/main/resources/application-dev.yml` 增加 `rocketmq.producer.group=replay-producer`，支撑回放发布生产者自动配置。
- 阶段验证通过：`mvn -pl replay-server -am test`，`common` 12 个测试通过，`replay-server` 65 个测试通过。
- 全量验证通过：`mvn test`，`common` 12 个测试通过，`logger-server` 59 个测试中 2 个按现有开关跳过，`replay-server` 65 个测试通过。
- 范围边界：Phase 04 只完成连续回放基础发布调度；跳转补偿、周期快照补发和控制命令编排按计划留给 Phase 05。

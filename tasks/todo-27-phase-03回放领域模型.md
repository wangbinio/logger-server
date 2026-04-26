# Phase 03 回放领域模型执行

## 背景

按照 `plan/005-回放系统设计/development-steps.md` 与 `phases/phase-03.md` 执行 Phase 03，实现 `replay-server` 的回放时钟、回放会话、状态机和会话管理器。

本阶段只处理内存状态和时间计算，不接入 TDengine 查询、RocketMQ 发布和调度器。

## 执行计划

- [x] 检查 Phase 03 设计、总设计回放领域模型章节和现有记录侧领域模型。
- [x] 将 Phase 03 状态标记为进行中。
- [x] 先补 `ReplayClockTest` 并确认失败。
- [x] 先补 `ReplaySessionState` 测试并确认失败。
- [x] 先补 `ReplaySessionTest` 并确认失败。
- [x] 先补 `ReplaySessionManagerTest` 并确认失败。
- [x] 实现 `ReplayClock`、`ReplaySessionState`、`ReplaySession` 和 `ReplaySessionManager`。
- [x] 运行 `mvn -pl replay-server -am test` 验证阶段测试。
- [x] 运行完整 `mvn test` 回归。
- [x] 回写 Phase 03 文档、开发步骤索引和本任务单 Review。

## Review

Phase 03 已完成。实际落地内容包括回放时钟、回放会话状态枚举、回放会话聚合根、回放会话管理器，以及 `ReplaySessionManager` 的 Spring Bean 装配。

TDD 记录：

- 已先新增 Phase 03 领域模型测试。
- 首次有效阶段测试因 `ReplayClock`、`ReplaySession` 等生产类缺失而编译失败，符合先失败测试再实现的预期。
- 补齐生产实现后，Phase 03 定向测试、`replay-server` 阶段测试和完整 `mvn test` 均通过。

验证结果：

- `mvn -pl replay-server -am -DfailIfNoTests=false -Dtest='ReplayClockTest,ReplaySessionStateTest,ReplaySessionTest,ReplaySessionManagerTest' test`：13 个测试通过。
- `mvn -pl replay-server -am test`：`common` 12 个测试、`replay-server` 56 个测试通过。
- `mvn test`：全 reactor 成功；`logger-server` 59 个测试中 1 个真实环境开关测试按既有机制跳过，`replay-server` 56 个测试通过。

遗留风险：

- 本阶段不接 TDengine 查询结果装配、不启动回放调度、不发布 RocketMQ 消息。
- 水位推进已在领域模型中限制为显式向前推进，但真正绑定“发布成功后推进”的业务调用留给 Phase 04。
- 时间跳转当前只具备领域时钟能力，事件补偿和周期快照发布留给 Phase 05。

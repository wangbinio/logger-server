# Phase 06 联调验收与交付收尾任务单

## 执行依据

- `plan/005-回放系统设计/005-final.md`
- `plan/005-回放系统设计/development-steps.md`
- `plan/005-回放系统设计/phases/phase-06.md`
- `tasks/lessons.md`
- `logger-server/src/test/java/com/szzh/loggerserver/integration/RealEnvironmentFullFlowTest.java`

## 计划

- [x] 将 Phase 06 状态标记为进行中，确认 Phase 00-05 已完成的实现边界。
- [x] 按 TDD 新增 `ReplayMetrics` 指标测试，覆盖活跃会话数、发布成功数、发布失败数、查询失败数、跳转次数和状态冲突数。
- [x] 实现 `ReplayMetrics`，并接入生命周期、控制、调度、发布与跳转主链路。
- [x] 新增 Mock 全链路集成测试，覆盖创建、元信息发布、启动、暂停、继续、倍速、向前跳转、向后跳转和停止。
- [x] 打通 Mock 集成测试，确保不依赖真实 TDengine/RocketMQ。
- [x] 新增真实环境测试开关 `-Dreplay.real-env.test=true`，默认不进入普通测试。
- [x] 实现真实环境回放测试，直接使用当前 YAML 中的 TDengine 与 RocketMQ 配置，并在 TDengine 中记录真实测试数据用于回放。
- [x] 按日志约束补齐关键结构化日志，保持业务调用注释、log 与业务代码空行、单条 log 两行格式。
- [x] 执行模块级和全工程 Maven 测试；真实环境测试单独运行并记录结果。
- [x] 回写 `005-final.md`、`development-steps.md`、`phase-06.md` 和必要架构文档。
- [x] 在本任务单末尾补充 Review，记录实际改动、测试结果和遗留风险。

## Review

### 实际改动

- 新增 `ReplayMetrics` 内存级指标，并接入回放发布成功/失败、查询失败、跳转成功和控制状态冲突路径。
- 新增 `ReplayFlowIntegrationTest`，使用 Mock Repository 与 Mock RocketMQ Sender 打通回放主链路。
- 新增 `ReplayRealEnvironmentTest`，通过 `-Dreplay.real-env.test=true` 使用当前 YAML 的 TDengine 与 RocketMQ 配置执行真实回放验证。
- 新增 `application-test.yml` 和 `application-real.yml` 测试资源。
- 补齐回放生命周期、控制、调度失败、发布失败路径的结构化日志字段。
- 回写 `005-final.md`、`development-steps.md`、`phase-06.md` 和 `ARCHITECTURE.md`。

### 验证结果

- `mvn -pl replay-server -am "-Dtest=ReplayMetricsTest,ReplaySituationPublisherTest,ReplaySchedulerTest,ReplayControlServiceTest,ReplayJumpServiceTest" -DfailIfNoTests=false test`：通过，21 个测试成功。
- `mvn -pl replay-server -am "-Dtest=ReplayFlowIntegrationTest" -DfailIfNoTests=false test`：通过，1 个 Mock 全链路测试成功。
- `mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" -DfailIfNoTests=false test`：通过，真实环境测试默认跳过。
- `mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test`：通过，真实 TDengine 与 RocketMQ 回放链路成功。
- `mvn test`：通过，全工程 91 个常规测试成功，1 个真实环境测试按默认策略跳过。

### 遗留风险

- 真实环境测试会在当前 TDengine `logger` 库留下 `replay-real-it-*` 相关表，用于保留真实回放数据证据。
- 第一版仍不支持同一实例并发记录和回放，不支持 `sourceInstanceId` 与 `replayInstanceId` 分离。
- 回放指标当前为内存级计数器，尚未接入 Micrometer 或外部可观测性系统。

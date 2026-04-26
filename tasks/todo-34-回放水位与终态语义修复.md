# 回放水位与终态语义修复任务单

## 执行依据

- `plan/006-回放系统交付审阅/fix-steps.md`
- `plan/006-回放系统交付审阅/phases/phase-00.md`
- `plan/006-回放系统交付审阅/006-回放系统交付审阅-draft.md`
- `plan/005-回放系统设计/005-final.md`
- `tasks/todo-31-回放系统交付审阅.md`

## 计划

- [x] 将 Phase 00 文档状态标记为进行中。
- [x] 补充初始水位、首帧发布、完成态跳转、终态停止清理失败测试。
- [x] 运行阶段定向测试，确认新增测试暴露当前语义偏差。
- [x] 修复 `ReplaySession` 初始水位与水位边界。
- [x] 修复 `COMPLETED` 可跳转语义。
- [x] 修复 `COMPLETED` 和 `FAILED` 停止后可释放会话与订阅语义。
- [x] 运行 Phase 00 定向测试。
- [x] 按需运行 `mvn -pl replay-server -am test` 回归。
- [x] 回写 Phase 00 Review、修复索引状态和本任务单 Review。

## Review

### 实际改动

- `ReplaySession` 初始水位改为 `simulationStartTime - 1`，避免首次 `(from, to]` 窗口漏发 `simtime == simulationStartTime` 的帧；`Long.MIN_VALUE` 起始时间保留原值避免下溢。
- `ReplaySessionState.COMPLETED` 调整为自然完成态，不再作为释放终态；`STOPPED`、`FAILED` 仍为释放终态。
- `ReplaySession.jumpTo()` 接受 `COMPLETED` 并在跳转成功后转入 `PAUSED`，`ReplayControlService` 与 `ReplayJumpService` 同步放开完成态跳转入口。
- `ReplaySession.stop()` 与 `ReplaySessionManager.stopSession()` 支持停止 `COMPLETED` 和 `FAILED` 会话，`ReplayLifecycleService.stopReplay()` 因此能释放调度、订阅和会话对象。
- 测试同步覆盖 Phase 00 语义，并更新 Mock 集成测试、活跃会话指标和状态枚举契约。

### 验证结果

- 红灯验证：`mvn -pl replay-server -am "-Dtest=ReplaySessionTest,ReplaySessionManagerTest,ReplaySchedulerTest,ReplayControlServiceTest,ReplayLifecycleServiceTest" -DfailIfNoTests=false test` 在实现前失败，符合 TDD 预期。
- 定向验证：同一命令在实现后通过，32 个测试成功。
- 关联验证：`mvn -pl replay-server -am "-Dtest=ReplayJumpServiceTest" -DfailIfNoTests=false test` 通过，5 个测试成功。
- 模块验证：`mvn -pl replay-server -am test` 通过，101 个测试执行，1 个真实环境测试默认跳过。
- 全量验证：`mvn clean test` 通过，清理历史报告后 surefire XML 汇总为 `Tests=172, Failures=0, Errors=0, Skipped=2`。

### 遗留风险

- Phase 01 仍需处理调度 tick 与 jump 发布互斥，以及跳转发布失败后进入 `FAILED` 且不恢复调度。
- 本阶段未开启真实环境测试；真实 TDengine/RocketMQ 验证继续由后续阶段按显式开关处理。

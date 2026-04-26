# 调度互斥与跳转失败语义修复任务单

## 执行依据

- `plan/006-回放系统交付审阅/fix-steps.md`
- `plan/006-回放系统交付审阅/phases/phase-01.md`
- `tasks/todo-31-回放系统交付审阅.md`
- `plan/005-回放系统设计/005-final.md`

## 计划

- [x] 将 Phase 01 文档状态标记为进行中。
- [x] 补充跳转发布失败进入 `FAILED` 测试。
- [x] 补充跳转失败后不恢复调度测试。
- [x] 补充连续调度与时间跳转互斥测试。
- [x] 实现会话级发布互斥，覆盖连续窗口查询、发布和水位推进。
- [x] 修复跳转发布失败状态，失败后不推进水位、不恢复运行态。
- [x] 修复控制服务调度恢复判断，跳转失败后不得重新 schedule。
- [x] 运行 Phase 01 定向测试。
- [x] 运行 `mvn -pl replay-server -am test` 和必要全量回归。
- [x] 回写 Phase 01 Review、修复索引状态和本任务单 Review。

## Review

### 完成内容

- 已在 `ReplayScheduler.tick()` 与 `ReplayJumpService.jump()` 之间建立同一 `ReplaySession` 监视器互斥，保证同实例连续窗口和跳转补偿不会并发查询、发布或推进水位。
- 已修复跳转失败语义：跳转查询或发布失败后会话进入 `FAILED`，`lastDispatchedSimTime` 不伪装推进，运行态跳转失败后不恢复 `RUNNING`。
- 已修复控制层恢复调度判断：`ReplayControlService.handleJump()` 只在 `jumpService.jump()` 成功返回且会话仍为 `RUNNING` 时重新 schedule。
- 已补充跳转发布失败、查询失败、控制层不恢复调度、连续 tick 与 jump 互斥、Mock 全链路失败结果等测试覆盖。

### 验证结果

- `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest,ReplayControlServiceTest,ReplayFlowIntegrationTest" -DfailIfNoTests=false test`：通过，23 个测试，0 失败，0 错误，0 跳过。
- `mvn clean test`：通过，四模块 Reactor 均 SUCCESS。
- clean 后 surefire XML 汇总：48 个报告文件、176 个测试、0 失败、0 错误、2 跳过。
- 跳过项为默认不开启的真实环境测试：`RealEnvironmentFullFlowTest`、`ReplayRealEnvironmentTest`。

### 后续边界

- 本阶段未开启 `-Dreplay.real-env.test=true`，真实 TDengine/RocketMQ 全链路入口验证留给 Phase 03。
- 会话锁覆盖查询与发布可以保证正确性，但长耗时窗口会增加同实例控制命令等待时间，后续真实环境阶段需要继续观察。

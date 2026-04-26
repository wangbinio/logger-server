# Phase 01 调度互斥与跳转失败语义修复

当前状态：已完成

## 1. 阶段目标

修复交付审阅中两个最高风险并发与失败语义问题：

- 连续调度 tick 与时间跳转发布必须真正互斥。
- 跳转补偿或周期快照发布失败后，会话必须进入 `FAILED`，且外层不得重新恢复调度。

## 2. 实现思路

先补并发行为测试和失败状态测试。并发测试应制造一个正在执行的 `ReplayScheduler.tick()`，让其阻塞在查询或发布阶段，再触发 `ReplayControlService.handleJump()`，证明当前实现可能并发发布或提前恢复调度。

实现层建议引入明确的会话级发布互斥机制，例如在 `ReplaySession` 中增加专用锁对象或使用现有 `synchronized (session)` 包住调度发布和跳转发布的关键段。由于调度和跳转都涉及 TDengine 查询与 RocketMQ 发布，锁粒度必须覆盖“查询 -> 发布 -> 水位更新”这个原子业务窗口，避免跳转和连续窗口交错。

跳转失败时应与连续发布失败保持一致：发布失败后标记 `FAILED`，不推进水位，不恢复运行态，不重新 schedule。

## 3. 需要新增或修改的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/com/szzh/replayserver/domain/session/ReplaySession.java` | 如采用会话级发布锁，在会话聚合根中提供锁或串行执行方法。 |
| `replay-server/src/main/java/com/szzh/replayserver/service/ReplayScheduler.java` | 让连续窗口查询、发布和水位推进持有会话级互斥。 |
| `replay-server/src/main/java/com/szzh/replayserver/service/ReplayControlService.java` | 跳转失败后不得重新 schedule `FAILED` 会话。 |
| `replay-server/src/main/java/com/szzh/replayserver/service/ReplayJumpService.java` | 跳转发布失败时标记 `FAILED`，并保持水位和时钟不伪装成功。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplaySchedulerTest.java` | 新增调度锁行为和发布失败不推进水位回归测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplayControlServiceTest.java` | 新增跳转失败后不恢复调度测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplayJumpServiceTest.java` | 新增跳转发布失败进入 `FAILED` 测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/integration/ReplayFlowIntegrationTest.java` | 补充跳转与连续调度交错场景的集成验证。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 01-01 | 新增跳转发布失败状态测试 | 在 `ReplayJumpServiceTest` 中模拟 `ReplaySituationPublisher.publish` 抛异常，断言会话进入 `FAILED`。 | Phase 00 |
| 01-02 | 新增跳转失败不恢复调度测试 | 在 `ReplayControlServiceTest` 中模拟 `jumpService.jump` 使会话失败，断言不再调用 `scheduler.schedule`。 | 01-01 |
| 01-03 | 新增调度与跳转互斥测试 | 使用 `CountDownLatch` 或阻塞 mock，证明跳转不能和正在执行的 tick 并发发布。 | 01-02 |
| 01-04 | 实现会话级发布互斥 | 调整 `ReplayScheduler.tick()` 和 `ReplayJumpService.jump()` 的关键区，保证同一会话串行发布。 | 01-03 |
| 01-05 | 修复跳转失败状态 | 在跳转发布或查询失败后保留跳转前水位，标记 `FAILED`，避免恢复运行态。 | 01-01 |
| 01-06 | 修复外层调度恢复判断 | `ReplayControlService.handleJump()` 只在跳转成功且会话仍为 `RUNNING` 时恢复调度。 | 01-02, 01-05 |
| 01-07 | 运行阶段测试 | 运行 `ReplaySchedulerTest`、`ReplayJumpServiceTest`、`ReplayControlServiceTest`、`ReplayFlowIntegrationTest`。 | 01-04, 01-05, 01-06 |
| 01-08 | 回写阶段 Review | 记录互斥方案、失败语义和测试结果。 | 01-07 |

## 5. 验证要求

- 同一 `instanceId` 下，连续窗口发布和跳转补偿发布不能并发执行。
- 任何跳转发布失败都不推进 `lastDispatchedSimTime`。
- 跳转发布失败后会话进入 `FAILED`，且不会被 `ReplayControlService` 重新 schedule。
- 停止命令在跳转或调度发布过程中仍能最终释放资源；如本阶段不完全解决停止中断，必须在 Review 中记录后续阶段处理边界。

## 6. 当前无需澄清的问题

本阶段修复方向明确，暂无阻塞性疑问。

## Review

### 实际改动

- `ReplayScheduler.tick()` 对同一 `ReplaySession` 加会话级互斥，连续窗口的 TDengine 查询、RocketMQ 发布、水位推进和完成态判断在同一关键区内串行执行，避免与同实例跳转发布交错。
- `ReplayJumpService.jump()` 复用同一会话锁执行跳转补偿查询、补偿发布、周期快照发布和水位同步；查询或发布抛出运行时异常时，若会话尚未进入终态则标记为 `FAILED`，不再恢复运行态。
- `ReplayControlService.handleJump()` 增加跳转成功标记，仅在跳转成功、跳转前为运行态且会话仍为 `RUNNING` 时恢复调度，避免失败跳转在 `finally` 中重新 schedule。
- 新增和补强 `ReplaySchedulerTest`、`ReplayJumpServiceTest`、`ReplayControlServiceTest`、`ReplayFlowIntegrationTest`，覆盖跳转发布失败进入 `FAILED`、水位不推进、控制层不恢复调度，以及连续 tick 与跳转互斥。

### 验证结果

- `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest,ReplayControlServiceTest,ReplayFlowIntegrationTest" -DfailIfNoTests=false test`：通过，23 个测试，0 失败，0 错误，0 跳过。
- `mvn clean test`：通过，Reactor 四个模块均 SUCCESS；clean 后 surefire XML 汇总为 48 个报告文件、176 个测试、0 失败、0 错误、2 跳过。
- 跳过项为显式开关控制的真实环境测试：`RealEnvironmentFullFlowTest` 和 `ReplayRealEnvironmentTest`，本阶段未开启真实 TDengine/RocketMQ 验证。

### 遗留风险

- 本阶段锁粒度按阶段要求覆盖“查询 -> 发布 -> 水位推进”的原子业务窗口，同一实例跳转会等待正在执行的 tick 完成；如果后续真实环境下单窗口查询或发布耗时过长，需在 Phase 03/04 的真实环境和异步边界验证中继续观察控制命令延迟。
- 停止命令在调度或跳转发布进行中会等待已有关键区释放后完成资源清理；本阶段已保证跳转与连续发布串行，停止中断语义仍按后续阶段的真实入口验证继续收敛。

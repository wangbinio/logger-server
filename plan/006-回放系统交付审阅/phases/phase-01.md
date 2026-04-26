# Phase 01 调度互斥与跳转失败语义修复

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

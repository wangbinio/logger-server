# Phase 00 回放水位与终态语义修复

## 1. 阶段目标

修复交付审阅中与回放状态机和水位边界直接相关的问题：

- 初始水位应符合 `simulationStartTime - 1` 设计，避免漏发 `simtime == simulationStartTime` 的第一帧。
- `COMPLETED` 状态应允许时间跳转后重新查看。
- 停止 `COMPLETED` 或 `FAILED` 会话时仍应释放会话对象和实例级控制订阅。

## 2. 实现思路

先补状态机与调度边界测试，证明当前实现会漏发开始时间帧、拒绝完成态跳转、无法移除终态会话。再调整 `ReplaySession`、`ReplaySessionState`、`ReplayControlService`、`ReplayJumpService` 和 `ReplaySessionManager` 的状态判断。

`COMPLETED` 不应简单作为“彻底不可操作”的终态处理。建议把状态语义拆清楚：`STOPPED` 和 `FAILED` 是不可继续操作的释放/失败终态；`COMPLETED` 是自然完成态，仍保留订阅和会话，允许跳转查看，收到 stop 后释放。

## 3. 需要新增或修改的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/com/szzh/replayserver/domain/session/ReplaySession.java` | 调整初始水位和 `COMPLETED` 跳转状态判断。 |
| `replay-server/src/main/java/com/szzh/replayserver/domain/session/ReplaySessionState.java` | 拆分释放终态与自然完成态语义，避免 `COMPLETED` 阻断跳转。 |
| `replay-server/src/main/java/com/szzh/replayserver/domain/session/ReplaySessionManager.java` | 修复停止终态会话后仍能移除会话。 |
| `replay-server/src/main/java/com/szzh/replayserver/service/ReplayControlService.java` | 接受 `COMPLETED` 状态下的时间跳转。 |
| `replay-server/src/main/java/com/szzh/replayserver/service/ReplayJumpService.java` | 接受 `COMPLETED` 状态下的时间跳转，并保持跳转后状态语义清晰。 |
| `replay-server/src/test/java/com/szzh/replayserver/domain/session/ReplaySessionTest.java` | 新增初始水位和完成态跳转单元测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/domain/session/ReplaySessionManagerTest.java` | 新增终态停止移除测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplaySchedulerTest.java` | 新增 `simtime == simulationStartTime` 首帧发布测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplayControlServiceTest.java` | 新增 `COMPLETED` 状态跳转委托测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplayLifecycleServiceTest.java` | 新增停止完成态或失败态会话释放资源测试。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 00-01 | 新增初始水位失败测试 | 构造 `simulationStartTime=1000` 的会话，断言初始 `lastDispatchedSimTime=999`，并验证连续窗口能发布 `simtime=1000` 帧。 | 无 |
| 00-02 | 新增完成态跳转失败测试 | 构造 `COMPLETED` 会话，发送 jump 控制，先验证当前实现拒绝或记录冲突。 | 00-01 |
| 00-03 | 新增终态停止清理失败测试 | 覆盖 `COMPLETED` 和 `FAILED` 会话收到 stop 后从 `ReplaySessionManager` 移除。 | 00-02 |
| 00-04 | 修复初始水位 | 将会话初始水位改为 `simulationStartTime - 1`，并处理开始时间为极小值时的边界。 | 00-01 |
| 00-05 | 修复完成态跳转语义 | 允许 `COMPLETED` 状态接受跳转；跳转后建议进入 `PAUSED` 或保持一个可查看状态，需测试明确。 | 00-02 |
| 00-06 | 修复终态停止清理 | 调整 `stopSession` 与 `removeSession`，确保 stop 命令对已完成或失败会话仍释放会话对象。 | 00-03 |
| 00-07 | 运行阶段测试 | 运行 `ReplaySessionTest`、`ReplaySessionManagerTest`、`ReplaySchedulerTest`、`ReplayControlServiceTest`、`ReplayLifecycleServiceTest`。 | 00-04, 00-05, 00-06 |
| 00-08 | 回写阶段 Review | 在本文档末尾记录实际改动、测试结果和仍需后续阶段处理的风险。 | 00-07 |

## 5. 验证要求

- 连续回放首次窗口能包含 `simtime == simulationStartTime` 的帧。
- 自然完成后仍保留 `broadcast-{instanceId}` 订阅和会话，且能执行时间跳转。
- 停止 `READY`、`RUNNING`、`PAUSED`、`COMPLETED`、`FAILED` 会话都应释放实例订阅并移除会话。
- 修复后不改变 `STOPPED` 和 `FAILED` 不允许继续启动/暂停/倍速的语义。

## 6. 当前无需澄清的问题

本阶段可以按设计直接执行，暂无阻塞性疑问。

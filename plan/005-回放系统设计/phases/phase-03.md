# Phase 03 回放领域模型

## 1. 阶段目标

实现回放系统的核心领域模型：

- `ReplayClock`
- `ReplaySession`
- `ReplaySessionState`
- `ReplaySessionManager`

本阶段只处理内存状态和时间计算，不接入 RocketMQ 发布和 TDengine 查询。

## 2. 实现思路

`ReplayClock` 不复用记录侧 `SimulationClock`，而是从已记录的仿真开始时间启动，并维护结束时间边界。`ReplaySession` 聚合一次回放任务所需的时间范围、表描述、回放时钟、状态、水位和订阅句柄。`ReplaySessionManager` 使用 `ConcurrentHashMap` 管理会话，并保证创建、查询、停止和移除的幂等语义。

同一实例控制需要串行化，因此会话状态迁移方法应使用同步保护。

## 3. 需要新增的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/.../domain/clock/ReplayClock.java` | 回放时钟。 |
| `replay-server/src/main/java/.../domain/session/ReplaySessionState.java` | 回放会话状态枚举。 |
| `replay-server/src/main/java/.../domain/session/ReplaySession.java` | 回放会话聚合根。 |
| `replay-server/src/main/java/.../domain/session/ReplaySessionManager.java` | 回放会话管理器。 |
| `replay-server/src/test/java/.../domain/clock/ReplayClockTest.java` | 回放时钟测试。 |
| `replay-server/src/test/java/.../domain/session/ReplaySessionTest.java` | 回放会话测试。 |
| `replay-server/src/test/java/.../domain/session/ReplaySessionManagerTest.java` | 会话管理器测试。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 03-01 | 新增 `ReplayClockTest` | 覆盖指定起点启动、暂停冻结、继续推进、倍率调整、跳转边界和结束时间封顶。 | Phase 01 |
| 03-02 | 实现 `ReplayClock` | 使用 `LongSupplier` 支持可控时间源，所有公开状态变更方法使用 `synchronized`。 | 03-01 |
| 03-03 | 新增 `ReplaySessionState` 测试 | 验证终态识别，终态包括 `STOPPED`、`COMPLETED`、`FAILED`。 | 03-02 |
| 03-04 | 实现 `ReplaySessionState` | 定义 `PREPARING`、`READY`、`RUNNING`、`PAUSED`、`STOPPED`、`COMPLETED`、`FAILED`。 | 03-03 |
| 03-05 | 新增 `ReplaySessionTest` | 覆盖状态迁移、水位更新、订阅句柄设置、停止幂等和终态迁移限制。 | 03-04 |
| 03-06 | 实现 `ReplaySession` | 保存时间范围、表分类结果、时钟、水位、状态和运行统计。 | 03-05 |
| 03-07 | 新增 `ReplaySessionManagerTest` | 覆盖创建、重复创建、查询、停止、移除已停止会话和会话数量统计。 | 03-06 |
| 03-08 | 实现 `ReplaySessionManager` | 使用 `ConcurrentHashMap` 管理会话，提供幂等创建与安全移除。 | 03-07 |
| 03-09 | 运行阶段测试 | 运行领域模型相关单元测试。 | 03-08 |

## 5. 验证要求

- `ReplayClock.start()` 必须从 `simulationStartTime` 启动，而不是从当前墙钟时间启动。
- `ReplayClock.currentTime()` 不能小于开始时间，不能大于结束时间。
- 状态终态后不允许迁移到其他状态。
- `lastDispatchedSimTime` 只能由成功发布路径推进，领域模型要提供明确方法支持该约束。

## 6. 当前无需澄清的问题

本阶段没有阻塞性疑问。

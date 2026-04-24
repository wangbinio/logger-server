# Phase 01：核心领域模型

## 实现思路

先实现最关键的领域模型：实例会话、生命周期状态与仿真时钟。这个阶段的目标是把运行时状态管理从“概念”变成“可测试对象”，为 MQ 消费和 TDengine 写入提供统一入口。

## 需要新增的文件

- `src/main/java/com/szzh/loggerserver/domain/session/SimulationSession.java`
- `src/main/java/com/szzh/loggerserver/domain/session/SimulationSessionState.java`
- `src/main/java/com/szzh/loggerserver/domain/session/SimulationSessionManager.java`
- `src/main/java/com/szzh/loggerserver/domain/clock/SimulationClock.java`
- `src/test/java/com/szzh/loggerserver/domain/clock/SimulationClockTest.java`
- `src/test/java/com/szzh/loggerserver/domain/session/SimulationSessionManagerTest.java`

## 待办步骤

- [ ] 定义 `SimulationSessionState`
  说明：明确 `PREPARING`、`READY`、`RUNNING`、`PAUSED`、`STOPPED`、`FAILED` 六种状态。
- [ ] 实现 `SimulationClock`
  说明：封装启动、暂停、继续、倍速结算逻辑，保证状态切换和时间计算可单测。
- [ ] 实现 `SimulationSession`
  说明：收敛 `instanceId`、时钟、状态、订阅句柄引用、统计字段和异常信息。
- [ ] 实现 `SimulationSessionManager`
  说明：负责会话创建、查询、停止、移除及幂等控制。
- [ ] 编写 `SimulationClockTest`
  说明：先覆盖启动、暂停、继续和倍率调整的失败/成功场景。
- [ ] 编写 `SimulationSessionManagerTest`
  说明：覆盖重复创建、重复停止、状态变迁与并发安全的基础场景。

## 预期完成标志

- 运行时状态模型不再依赖 MQ 或数据库。
- 领域对象具备稳定测试覆盖。

## 疑问或待澄清

当前无阻塞性疑问。

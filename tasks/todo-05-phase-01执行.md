# 任务：执行 Phase 01

## 背景

根据 `plan/002-详细开发步骤/development-steps.md` 与 `plan/002-详细开发步骤/phases/phase-01.md`，实现核心领域模型，包括会话状态机、仿真时钟和会话管理器。

## 执行项

- [x] 阅读阶段计划与当前骨架，确认 Phase 01 仅实现纯领域模型，不依赖 MQ 或数据库。
- [x] 先补 `SimulationClockTest` 与 `SimulationSessionManagerTest`，定义行为边界。
- [x] 实现 `SimulationSessionState`、`SimulationClock`、`SimulationSession`、`SimulationSessionManager`。
- [x] 完成静态校验并回写阶段状态与 review。

## 验收标准

- 领域模型不依赖 RocketMQ、TDengine 或 Spring 业务编排。
- 时钟支持启动、暂停、继续、倍率调整和当前仿真时间计算。
- 会话管理器支持幂等创建、查询、停止、移除和基本并发安全。

## Review

- 已新增 `SimulationClock`、`SimulationSessionState`、`SimulationSession`、`SimulationSessionManager` 四个核心领域类，且保持为纯领域模型，不依赖 MQ、数据库或 Spring 业务编排。
- 已新增 `SimulationClockTest` 与 `SimulationSessionManagerTest` 两个测试类，覆盖时钟推进、暂停/继续、倍率切换、重复创建、重复停止和并发创建等核心场景。
- 已使用 `javac` 对 Phase 01 的四个主类执行静态编译，编译通过。
- 当前环境仍缺少 `mvn` 或 `mvnw`，因此未执行完整 JUnit 测试运行。

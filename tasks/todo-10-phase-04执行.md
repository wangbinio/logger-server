# 任务：Phase 04 执行

## 背景

根据 `plan\002-详细开发步骤\phases\phase-04.md`，本阶段需要在已完成的会话模型、TDengine 基础设施和 RocketMQ 动态订阅之上，补齐服务层编排，把“创建 -> 启动 -> 暂停 -> 继续 -> 停止 -> 态势入库”串成完整主链路，并用测试保护幂等与丢弃策略。

## 执行项

- [x] 复核 `phase-04.md`、`development-steps.md` 与总体设计，确认服务职责和状态机边界。
- [x] 先编写失败测试，覆盖生命周期服务、控制服务、态势记录服务的主要分支。
- [x] 实现 `TaskCreatePayload`，统一解析创建消息中的 `instanceId`。
- [x] 实现 `SimulationLifecycleService`，完成创建、重复创建、停止与资源回收编排。
- [x] 实现 `SimulationControlService`，完成启动、暂停、继续的时钟驱动与状态迁移。
- [x] 实现 `SituationRecordService`，完成状态校验、仿真时间计算、写库委派与丢弃计数。
- [x] 将三个服务接入现有 `GlobalBroadcastListener`、`InstanceBroadcastMessageHandler`、`SituationMessageHandler` 端口体系。
- [x] 使用 Java 8 运行 Maven 测试。
- [x] 回填 review。

## 验收标准

- 创建消息能够建立会话、建超表、建立实例级订阅，并在重复创建时保持幂等。
- 启动、暂停、继续消息能够正确驱动 `SimulationClock` 与 `SimulationSessionState`。
- 只有 `RUNNING` 状态的态势消息会写入 TDengine，其余场景会走丢弃计数而不是误写库。
- 停止消息能够取消订阅、停止会话并移除资源，重复停止安全返回。
- 服务层测试覆盖创建、重复创建、启动、暂停、继续、停止、无会话态势消息、非运行态态势消息、写库成功计数等关键路径。

## Review

- 已新增 `TaskCreatePayload`，统一从全局消息 JSON 中提取 `instanceId`，供创建与停止两条生命周期链路复用。
- 已新增 `SimulationLifecycleService`，实现创建时的会话建立、超表初始化、实例级订阅建立，以及停止时的订阅取消与会话移除；重复创建和重复停止都按幂等策略处理。
- 已新增 `SimulationControlService`，实现 `READY -> RUNNING`、`RUNNING -> PAUSED`、`PAUSED -> RUNNING` 的状态迁移，并驱动 `SimulationClock` 启停恢复。
- 已新增 `SituationRecordService`，实现会话存在性检查、仅 `RUNNING` 状态写库、仿真时间计算、写库失败记录与丢弃计数。
- 现有 `GlobalBroadcastListener`、`InstanceBroadcastMessageHandler`、`SituationMessageHandler` 已通过端口接口自动接入上述服务，无需改动 Phase 03 既有消息解析与订阅管理实现。
- 已新增 `SimulationLifecycleServiceTest`、`SimulationControlServiceTest`、`SituationRecordServiceTest`，覆盖创建、重复创建、初始化失败、停止回收、启动、暂停、继续、幂等控制、无会话态势消息、非运行态丢弃、运行态写库、写库失败记录等关键路径。
- 已先在缺失实现的情况下运行一次 `mvn -q test`，确认测试处于失败状态；随后补齐实现后再次在 Java 8 环境下运行通过。
  - `JAVA_HOME=C:\Users\summer\.jdks\corretto-1.8.0_482`
  - 命令：`mvn -q test`
